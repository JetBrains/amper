/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.devices.Abi
import com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG
import org.jetbrains.amper.cli.CliProblemReporterContext
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.core.get
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.util.AndroidSdkDetector
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.PlatformUtil
import org.jetbrains.amper.util.targetLeafPlatforms
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists

@Suppress("LoggingSimilarMessage")
class ProjectTasksBuilder(private val context: ProjectContext, private val model: Model) {

    private val androidSdkPath by lazy { AndroidSdkDetector().detectSdkPath() ?: error("Android SDK is not found") }

    fun build(): TaskGraph {
        // always process in fixed order, no other requirements yet
        val sortedByPath = model.modules.sortedBy { (it.source as PotatoModuleFileSource).buildFile }

        val tasks = TaskGraphBuilder()
        val executeOnChangedInputs = ExecuteOnChangedInputs(context.buildOutputRoot)

        for (module in sortedByPath) {
            val modulePlatforms = module.targetLeafPlatforms
            for (platform in modulePlatforms) {
                setupLogcatTask(platform, tasks, module)
                for (isTest in listOf(false, true)) {
                    val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }

                    val fragmentsCompileModuleDependencies = fragments
                        .flatMap { fragment -> fragment.externalDependencies.map { fragment to it } }
                        .mapNotNull { (fragment, dependency) ->
                            when (dependency) {
                                is MavenDependency -> null
                                is PotatoModuleDependency -> {
                                    // runtime dependencies are not required to be in compile tasks graph
                                    if (dependency.compile) {
                                        // TODO test with non-resolved dependency on module
                                        val resolvedDependencyModule = dependency.module

                                        resolvedDependencyModule
                                    } else {
                                        null
                                    }
                                }

                                else -> error(
                                    "Unsupported dependency type: '$dependency' " +
                                            "at module '${module.source}' fragment '${fragment.name}'"
                                )
                            }
                        }

                    val androidCompileDependencies = createCompileDependencies(platform, module, tasks, executeOnChangedInputs, isTest)
                    val androidPrepareDependencies = createPrepareDependencies(platform, module, tasks, executeOnChangedInputs, isTest)
                    val androidRunDependencies = createRunDependencies(platform, module, executeOnChangedInputs, tasks, isTest)

                    fun createResolveTask(): Task =
                        ResolveExternalDependenciesTask(
                            module,
                            context.userCacheRoot,
                            executeOnChangedInputs,
                            platform = platform,
                            fragments = fragments,
                            fragmentsCompileModuleDependencies = fragmentsCompileModuleDependencies,
                            taskName = getTaskName(module, CommonTaskType.DEPENDENCIES, platform, isTest = isTest)
                        ).also { resolveTask ->
                            tasks.registerTask(resolveTask)
                        }

                    createResolveTask()

                    val avdPath = if (platform == Platform.ANDROID) AndroidLocationsSingleton.avdLocation else null
                    val buildTypes = if (platform == Platform.ANDROID) listOf(BuildType.Debug, BuildType.Release) else listOf(BuildType.Debug)

                    for (buildType in buildTypes) {
                        if (isTest && fragments.all { !it.src.exists() }) {
                            // no test code, assume no code generation
                            // other modules could not depend on this module's tests, so it's ok
                            continue // TODO shouldn't we consistently keep these tasks in the graph even if they are noops?
                        }

                        val prepareAndroidBuildTaskName = setupPrepareAndroidTask(
                            platform,
                            module,
                            isTest,
                            tasks,
                            executeOnChangedInputs,
                            fragments,
                            buildType,
                            androidSdkPath,
                            androidPrepareDependencies + androidCompileDependencies,
                            context.userCacheRoot.path
                        )

                        val androidBuildTaskName = setupAndroidBuildTasks(
                            platform,
                            module,
                            tasks,
                            isTest,
                            executeOnChangedInputs,
                            fragments,
                            buildType,
                            androidSdkPath,
                            context.userCacheRoot.path,
                        )

                        fun createCompileTask(buildType: BuildType = BuildType.Debug): Task? {
                            val compileTaskName = getTaskName(
                                module = module,
                                type = CommonTaskType.COMPILE,
                                platform = platform,
                                isTest = isTest,
                            )
                            val top = platform.topmostParentNoCommon
                            val jvmCompileTask = JvmCompileTask(
                                module = module,
                                fragments = fragments,
                                userCacheRoot = context.userCacheRoot,
                                projectRoot = context.projectRoot,
                                taskOutputRoot = getTaskOutputPath(compileTaskName),
                                taskName = compileTaskName,
                                executeOnChangedInputs = executeOnChangedInputs,
                            )
                            return when (top) {
                                Platform.JVM -> jvmCompileTask

                                Platform.ANDROID -> AndroidCompileTask(
                                    jvmCompileTask = jvmCompileTask,
                                    buildType = buildType
                                )

                                Platform.NATIVE -> NativeCompileTask(
                                    module = module,
                                    platform = platform,
                                    userCacheRoot = context.userCacheRoot,
                                    taskOutputRoot = getTaskOutputPath(compileTaskName),
                                    executeOnChangedInputs = executeOnChangedInputs,
                                    taskName = compileTaskName,
                                    tempRoot = context.projectTempRoot,
                                    isTest = isTest,
                                )

                                else -> {
                                    logger.warn("$top is not supported yet")
                                    null
                                }
                            }?.also {
                                tasks.registerTask(it, dependsOn = buildList {
                                    add(getTaskName(module, CommonTaskType.DEPENDENCIES, platform, isTest = isTest))
                                    if (top == Platform.ANDROID) {
                                        prepareAndroidBuildTaskName?.let { add(it) }
                                        addAll(androidCompileDependencies)
                                    }
                                })
                            }
                        }

                        fun createRunTask() {
                            require(!isTest)
                            require(!module.type.isLibrary())

                            val runTaskName = getTaskName(module, CommonTaskType.RUN, platform, isTest = false, buildType)

                            if (!PlatformUtil.platformsMayRunOnCurrentSystem.contains(platform)) {
                                logger.debug(
                                    "Skipping creating run task '{}' since it could not run on current system",
                                    runTaskName
                                )
                                return
                            }

                            when (val top = platform.topmostParentNoCommon) {
                                Platform.JVM -> JvmRunTask(
                                    module = module,
                                    userCacheRoot = context.userCacheRoot,
                                    projectRoot = context.projectRoot,
                                    taskName = runTaskName,
                                    commonRunSettings = context.commonRunSettings,
                                )

                                Platform.ANDROID -> AndroidRunTask(
                                    runTaskName,
                                    module,
                                    buildType,
                                    androidSdkPath,
                                    avdPath ?: error("No avd path")
                                )

                                Platform.NATIVE -> NativeRunTask(
                                    module = module,
                                    projectRoot = context.projectRoot,
                                    taskName = runTaskName,
                                    platform = platform,
                                    commonRunSettings = context.commonRunSettings,
                                )

                                else -> {
                                    logger.warn("$top is not supported yet")
                                    null
                                }
                            }?.also {
                                tasks.registerTask(
                                    it,
                                    dependsOn = buildList {
                                        if (platform != Platform.ANDROID) {
                                            add(getTaskName(module, CommonTaskType.COMPILE, platform, isTest = false, buildType))
                                        }
                                        androidBuildTaskName?.let { add(it) }
                                        addAll(androidRunDependencies)
                                    }
                                )
                            }
                        }

                        fun createTestTask() {
                            require(isTest)

                            val testTaskName = getTaskName(module, CommonTaskType.TEST, platform, isTest = true)
                            if (!PlatformUtil.platformsMayRunOnCurrentSystem.contains(platform)) {
                                logger.debug(
                                    "Skipping creating test task '{}' since it could not run on current system",
                                    testTaskName
                                )
                                return
                            }

                            when (val top = platform.topmostParentNoCommon) {
                                Platform.JVM -> JvmTestTask(
                                    module = module,
                                    userCacheRoot = context.userCacheRoot,
                                    projectRoot = context.projectRoot,
                                    taskName = testTaskName,
                                    taskOutputRoot = getTaskOutputPath(testTaskName),
                                )

                                Platform.NATIVE -> NativeTestTask(
                                    module = module,
                                    projectRoot = context.projectRoot,
                                    taskName = testTaskName,
                                    platform = platform,
                                )

                                else -> {
                                    logger.warn("$top is not supported yet")
                                    null
                                }
                            }?.also {
                                tasks.registerTask(
                                    it,
                                    dependsOn = listOf(
                                        getTaskName(
                                            module,
                                            CommonTaskType.COMPILE,
                                            platform,
                                            isTest = true
                                        )
                                    )
                                )
                            }
                        }

                        createCompileTask(buildType)


                        // TODO this does not support runtime dependencies
                        //  and dependency graph for it will be different
                        if (isTest) {
                            createTestTask()
                        } else {
                            if (!module.type.isLibrary()) {
                                createRunTask()
                            }
                        }

                        // TODO In the future this code should be near compile task
                        // TODO What to do with fragment.fragmentDependencies?
                        //  I'm not sure, it's just test -> non-test dependency? Otherwise we build it by platforms
                        if (isTest) {
                            tasks.registerDependency(
                                taskName = getTaskName(module, CommonTaskType.COMPILE, platform, true, buildType),
                                dependsOn = getTaskName(module, CommonTaskType.COMPILE, platform, false, buildType),
                            )
                        }

                        fun registerCompileDependency(module: PotatoModule, dependsOn: PotatoModule) {
                            tasks.registerDependency(
                                taskName = getTaskName(
                                    module,
                                    CommonTaskType.COMPILE,
                                    platform,
                                    isTest = isTest,
                                    buildType
                                ),
                                dependsOn = getTaskName(
                                    dependsOn,
                                    CommonTaskType.COMPILE,
                                    platform,
                                    isTest = false,
                                    buildType
                                ),
                            )
                        }

                        for (compileModuleDependency in fragmentsCompileModuleDependencies) {
                            // direct dependencies
                            registerCompileDependency(module, compileModuleDependency)

                            // exported dependencies
                            val exportedModuleDependencies = compileModuleDependency.fragments
                                .filter { it.platforms.contains(platform) && !it.isTest }
                                .flatMap { it.externalDependencies }
                                .filterIsInstance<PotatoModuleDependency>()
                                .filter { it.compile && it.exported }
                                .map { it.module }

                            for (exportedModuleDependency in exportedModuleDependencies) {
                                registerCompileDependency(module, exportedModuleDependency)
                            }
                        }
                    }
                }
            }
        }

        return tasks.build()
    }

    private fun setupLogcatTask(
        platform: Platform,
        tasks: TaskGraphBuilder,
        module: PotatoModule
    ) {
        if (platform == Platform.ANDROID) {
            tasks.registerTask(
                LogcatTask(TaskName.fromHierarchy(listOf(module.userReadableName, "logcat"))),
                getTaskName(module, CommonTaskType.RUN, platform, isTest = false, BuildType.Debug)
            )
        }
    }

    private fun createRunDependencies(
        platform: Platform,
        module: PotatoModule,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        tasks: TaskGraphBuilder,
        isTest: Boolean
    ): List<TaskName> {
        return buildList {
            if (platform == Platform.ANDROID) {
                val downloadSystemImageTaskName = setupDownloadSystemImageTask(
                    module,
                    isTest,
                    executeOnChangedInputs,
                    tasks
                )
                add(downloadSystemImageTaskName)
                val downloadAndroidEmulatorTaskName = TaskName.fromHierarchy(
                    listOf(
                        module.userReadableName,
                        "downloadAndroidEmulator${isTest.testSuffix}"
                    )
                )
                tasks.registerTask(
                    GetAndroidPlatformFileFromPackageTask(
                        "emulator",
                        executeOnChangedInputs,
                        androidSdkPath,
                        downloadAndroidEmulatorTaskName
                    )
                )
                add(downloadAndroidEmulatorTaskName)
            }
        }
    }

    private fun createCompileDependencies(
        platform: Platform,
        module: PotatoModule,
        tasks: TaskGraphBuilder,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        isTest: Boolean
    ): List<TaskName>  {
        return buildList {
            if (platform == Platform.ANDROID) {
                val androidPlatformJarTaskName = setupAndroidPlatformTask(
                    module,
                    tasks,
                    executeOnChangedInputs,
                    androidSdkPath,
                    isTest,
                )
                add(androidPlatformJarTaskName)
            }
        }
    }

    private fun createPrepareDependencies(
        platform: Platform,
        module: PotatoModule,
        tasks: TaskGraphBuilder,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        isTest: Boolean
    ): List<TaskName> {
        return buildList {
            if (platform == Platform.ANDROID) {
                val androidFragment = getAndroidFragment(module, isTest)
                val downloadBuildToolsTaskName =
                    TaskName.fromHierarchy(listOf(module.userReadableName, "downloadBuildTools${isTest.testSuffix}"))
                tasks.registerTask(
                    GetAndroidPlatformFileFromPackageTask(
                        "build-tools;${androidFragment?.settings?.android?.targetSdk?.versionNumber}.0.0",
                        executeOnChangedInputs,
                        androidSdkPath,
                        downloadBuildToolsTaskName
                    )
                )
                add(downloadBuildToolsTaskName)

                val downloadPlatformTools =
                    TaskName.fromHierarchy(listOf(module.userReadableName, "downloadPlatformTools${isTest.testSuffix}"))
                tasks.registerTask(
                    GetAndroidPlatformFileFromPackageTask(
                        "platform-tools",
                        executeOnChangedInputs,
                        androidSdkPath,
                        downloadPlatformTools
                    )
                )
                add(downloadPlatformTools)
            }
        }
    }

    // All task binding between themselves happens here, so let's keep it private here
    // This function is the only place where we define task naming convention
    private fun getTaskName(
        module: PotatoModule,
        type: CommonTaskType,
        platform: Platform,
        isTest: Boolean,
        buildType: BuildType? = null
    ): TaskName {
        val platformSuffix = platform.pretty.replaceFirstChar { it.uppercase() }

        val taskName = when (type) {
            // name convention <taskname><platform><buildtype><testsuffix>: example compileAndroidDebugTest
            CommonTaskType.COMPILE -> "compile$platformSuffix${buildType?.suffix(platform) ?: ""}${isTest.testSuffix}"
            CommonTaskType.DEPENDENCIES -> "resolveDependencies$platformSuffix${isTest.testSuffix}"
            CommonTaskType.RUN -> {
                require(!isTest)
                "run$platformSuffix${buildType?.suffix(platform) ?: ""}"
            }

            CommonTaskType.TEST -> {
                require(isTest)
                "test$platformSuffix${buildType?.suffix(platform) ?: ""}"
            }
        }

        return TaskName.fromHierarchy(listOf(module.userReadableName, taskName))
    }

    // Keep it private, code in any other class should not get direct access to the output path of a particular task
    // Let's assume every task may get another task output only from its dependencies
    // private getTaskOutputPath helps to enforce that in a gentle way
    private fun getTaskOutputPath(taskName: TaskName): TaskOutputRoot {
        val out = context.buildOutputRoot.path.resolve("tasks").resolve(taskName.name.replace(':', '_'))
        return TaskOutputRoot(path = out)
    }

    private fun setupPrepareAndroidTask(
        platform: Platform,
        module: PotatoModule,
        isTest: Boolean,
        tasks: TaskGraphBuilder,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        fragments: List<Fragment>,
        buildType: BuildType,
        androidSdkPath: Path,
        prepareAndroidTaskDependencies: List<TaskName>,
        userCacheRootPath: Path,
    ) = if (platform == Platform.ANDROID) {
        val prepareAndroidBuildName = TaskName.fromHierarchy(
            listOf(
                module.userReadableName, "prepareBuildAndroid${buildType.name}${isTest.testSuffix}"
            )
        )
        tasks.registerTask(
            AndroidPrepareTask(
                module,
                buildType,
                executeOnChangedInputs,
                androidSdkPath,
                fragments,
                userCacheRootPath,
                prepareAndroidBuildName
            ),
            prepareAndroidTaskDependencies
        )
        prepareAndroidBuildName
    } else null

    private fun setupAndroidBuildTasks(
        platform: Platform,
        module: PotatoModule,
        tasks: TaskGraphBuilder,
        isTest: Boolean,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        fragments: List<Fragment>,
        buildType: BuildType,
        androidSdkPath: Path,
        userCacheRootPath: Path
    ): TaskName? = if (platform == Platform.ANDROID) {
        val buildAndroidBuildName = TaskName
            .fromHierarchy(listOf(module.userReadableName, "finalizeBuildAndroid${isTest.testSuffix}${buildType.suffix(platform)}"))
        tasks.registerTask(
            AndroidBuildTask(
                module,
                buildType,
                executeOnChangedInputs,
                androidSdkPath,
                fragments,
                userCacheRootPath,
                getTaskOutputPath(buildAndroidBuildName),
                buildAndroidBuildName
            ),
            listOf(
                getTaskName(module, CommonTaskType.DEPENDENCIES, platform, isTest),
                getTaskName(module, CommonTaskType.COMPILE, platform, isTest, buildType)
            )
        )
        buildAndroidBuildName
    } else null

    private fun setupDownloadSystemImageTask(
        module: PotatoModule,
        isTest: Boolean,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        tasks: TaskGraphBuilder
    ): TaskName {
        val androidFragment = getAndroidFragment(module, isTest)
        val versionNumber = androidFragment?.settings?.android?.targetSdk?.versionNumber ?: 34

        val downloadSystemImageTaskName =
            TaskName.fromHierarchy(listOf(module.userReadableName, "downloadSystemImage${isTest.testSuffix}"))
        val abi = if(DefaultSystemInfo.detect().arch == SystemInfo.Arch.X64) Abi.X86_64 else Abi.ARM64_V8A
        tasks.registerTask(
            GetAndroidPlatformFileFromPackageTask(
                "system-images;android-$versionNumber;${DEFAULT_TAG.id};$abi",
                executeOnChangedInputs,
                androidSdkPath,
                downloadSystemImageTaskName
            )
        )

        return downloadSystemImageTaskName
    }

    private fun getAndroidFragment(module: PotatoModule, isTest: Boolean): LeafFragment? = module
        .fragments
        .filterIsInstance<LeafFragment>()
        .filter { it.isTest == isTest }.firstOrNull { Platform.ANDROID in it.platforms }

    private fun setupAndroidPlatformTask(
        module: PotatoModule,
        tasks: TaskGraphBuilder,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        androidSdkPath: Path,
        isTest: Boolean,
    ): TaskName {
        val androidFragment = getAndroidFragment(module, isTest)
        val targetSdk = androidFragment?.settings?.android?.targetSdk?.versionNumber ?: 34
        val downloadAndroidSdkTaskName =
            TaskName.fromHierarchy(listOf(module.userReadableName, "downloadSdkAndroid${isTest.testSuffix}"))
        tasks.registerTask(
            GetAndroidPlatformJarTask(
                GetAndroidPlatformFileFromPackageTask(
                    "platforms;android-$targetSdk",
                    executeOnChangedInputs,
                    androidSdkPath,
                    downloadAndroidSdkTaskName
                )
            )
        )
        return downloadAndroidSdkTaskName
    }

    private val Boolean.testSuffix: String
        get() = if(this) "Test" else ""

    // All task binding between themselves happens here, so let's keep it private
    private enum class CommonTaskType {
        COMPILE,
        DEPENDENCIES,
        RUN,
        TEST,
    }

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
}
