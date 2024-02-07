/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import com.android.sdklib.devices.Abi
import com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG
import org.jetbrains.amper.cli.CliProblemReporterContext
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.core.get
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
import java.nio.file.Paths
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
                val downloadAndroidEmulatorTaskName = if (platform == Platform.ANDROID) {
                    val downloadAndroidEmulatorTaskName = TaskName.fromHierarchy(listOf(module.userReadableName, "downloadAndroidEmulator"))
                    tasks.registerTask(
                        GetAndroidPlatformFileFromPackageTask(
                            "emulator",
                            executeOnChangedInputs,
                            androidSdkPath,
                            downloadAndroidEmulatorTaskName
                        )
                    )
                    downloadAndroidEmulatorTaskName
                } else null

                val downloadPlatformTools = if (platform == Platform.ANDROID) {
                    val downloadPlatformTools = TaskName.fromHierarchy(listOf(module.userReadableName, "downloadPlatformTools"))
                    tasks.registerTask(
                        GetAndroidPlatformFileFromPackageTask(
                            "platform-tools",
                            executeOnChangedInputs,
                            androidSdkPath,
                            downloadPlatformTools
                        )
                    )
                    downloadPlatformTools
                } else null

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
                                        val resolvedDependencyModule = dependency.resolveModuleDependency()

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

                    val downloadSystemImageTaskName = setupDownloadSystemImageTask(
                        module,
                        isTest,
                        executeOnChangedInputs,
                        tasks,
                        platform
                    )
                    val androidPlatformJarTaskName = setupAndroidPlatformTask(
                        module,
                        tasks,
                        executeOnChangedInputs,
                        androidSdkPath,
                        isTest,
                        platform
                    )

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
                            tasks.registerTask(resolveTask, dependsOn = buildList {
                                if (platform.topmostParentNoCommon == Platform.ANDROID) {
                                    androidPlatformJarTaskName?.let { add(it) }
                                }
                            })
                        }

                    createResolveTask()

                    val avdPath = if (platform == Platform.ANDROID) {
                        AndroidSdkDetector(buildList {
                            add(AndroidSdkDetector.EnvironmentVariableSuggester("ANDROID_AVD_HOME"))
                            add(AndroidSdkDetector.SystemPropertySuggester("ANDROID_AVD_HOME"))
                            add(object: AndroidSdkDetector.Suggester {
                                override fun suggestSdkPath(): Path? = System.getProperty("user.home")?.let { Paths.get(it).resolve(".android/avd") }
                            })
                        }).detectSdkPath()
                    } else null

                    val buildTypes = if (platform == Platform.ANDROID) listOf(BuildType.Debug, BuildType.Release) else listOf(BuildType.Default)

                    for (buildType in buildTypes) {
                        if (isTest && fragments.all { !it.src.exists() }) {
                            // no test code, assume no code generation
                            // other modules could not depend on this module's tests, so it's ok
                            continue // TODO shouldn't we consistently keep these tasks in the graph even if they are noops?
                        }

                        val prepareAndroidBuildTaskName = setupPrepareAndroidTasks(
                            platform,
                            module,
                            isTest,
                            tasks,
                            executeOnChangedInputs,
                            fragments,
                            buildType,
                            androidSdkPath
                        )

                        val androidBuildTasksName = setupAndroidBuildTasks(
                            platform,
                            module,
                            tasks,
                            isTest,
                            executeOnChangedInputs,
                            fragments,
                            buildType,
                            androidSdkPath
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
                                        androidPlatformJarTaskName?.let { add(it) }
                                        prepareAndroidBuildTaskName?.let { add(it) }
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
                                )

                                Platform.ANDROID -> AndroidRunTask(
                                    runTaskName,
                                    module,
                                    androidSdkPath,
                                    avdPath ?: error("No avd path")
                                )

                                Platform.NATIVE -> NativeRunTask(
                                    module = module,
                                    projectRoot = context.projectRoot,
                                    taskName = runTaskName,
                                    platform = platform,
                                )

                                else -> {
                                    logger.warn("$top is not supported yet")
                                    null
                                }
                            }?.also {
                                tasks.registerTask(
                                    it,
                                    dependsOn = buildList {
                                        add(getTaskName(module, CommonTaskType.COMPILE, platform, isTest = false, buildType))
                                        androidBuildTasksName?.let { add(it) }
                                        downloadAndroidEmulatorTaskName?.let { add(it) }
                                        downloadPlatformTools?.let { add(it) }
                                        downloadSystemImageTaskName?.let { add(it) }
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
                                .map { it.resolveModuleDependency() }

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

    private fun PotatoModuleDependency.resolveModuleDependency(): PotatoModule = with(CliProblemReporterContext()) {
        val result = model.module.get()

        if (problemReporter.wereProblemsReported()) {
            error("failed to build tasks graph, refer to the errors above")
        }

        result
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
        val testSuffix = if (isTest) "Test" else ""
        val platformSuffix = platform.pretty.replaceFirstChar { it.uppercase() }

        val taskName = when (type) {
            // name convention <taskname><platform><buildtype><testsuffix>: example compileAndroidDebugTest
            CommonTaskType.COMPILE -> "compile$platformSuffix${buildType?.suffix ?: ""}$testSuffix"
            CommonTaskType.DEPENDENCIES -> "resolveDependencies$platformSuffix$testSuffix"
            CommonTaskType.RUN -> {
                require(!isTest)
                "run$platformSuffix${buildType?.suffix ?: ""}"
            }

            CommonTaskType.TEST -> {
                require(isTest)
                "test$platformSuffix${buildType?.suffix ?: ""}"
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

    private fun setupPrepareAndroidTasks(
        platform: Platform,
        module: PotatoModule,
        isTest: Boolean,
        tasks: TaskGraphBuilder,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        fragments: List<Fragment>,
        buildType: BuildType,
        androidSdkPath: Path
    ) = if (platform == Platform.ANDROID) {
        val testSuffix = if (isTest) "Test" else ""
        val prepareAndroidBuildName = TaskName.fromHierarchy(
            listOf(
                module.userReadableName, "prepareBuildAndroid${buildType.name}${testSuffix}"
            )
        )

        tasks.registerTask(
            AndroidPrepareTask(
                module,
                buildType,
                executeOnChangedInputs,
                androidSdkPath,
                fragments,
                prepareAndroidBuildName
            )
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
        androidSdkPath: Path
    ): TaskName? {
        return if (platform == Platform.ANDROID) {
            val testSuffix = if (isTest) "Test" else ""
            val buildAndroidBuildName = TaskName.fromHierarchy(
                listOf(
                    module.userReadableName, "finalizeBuildAndroid${testSuffix}${buildType.suffix}"
                )
            )
            tasks.registerTask(
                AndroidBuildTask(module, buildType, executeOnChangedInputs, androidSdkPath, fragments, buildAndroidBuildName),
                listOf(
                    getTaskName(module, CommonTaskType.DEPENDENCIES, platform, isTest),
                    getTaskName(module, CommonTaskType.COMPILE, platform, isTest, buildType)
                )
            )
            return buildAndroidBuildName
        } else null
    }

    private fun setupDownloadSystemImageTask(
        module: PotatoModule,
        isTest: Boolean,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        tasks: TaskGraphBuilder,
        platform: Platform
    ): TaskName? {
        if (platform == Platform.ANDROID) {
            val fragments = module.fragments
                .filterIsInstance<LeafFragment>()
                .filter { it.isTest == isTest }
                .filter { Platform.ANDROID in it.platforms }
            val androidFragment = fragments.firstOrNull()
            val testSuffix = if (isTest) "Test" else ""

            return androidFragment?.let {
                val downloadSystemImageTaskName = TaskName.fromHierarchy(listOf(module.userReadableName, "downloadSystemImage$testSuffix"))
                tasks.registerTask(
                    GetAndroidPlatformFileFromPackageTask(
                        "system-images;android-${it.settings.android.targetSdk.versionNumber};${DEFAULT_TAG.id};${Abi.ARM64_V8A}",
                        executeOnChangedInputs,
                        androidSdkPath,
                        downloadSystemImageTaskName
                    )
                )

                downloadSystemImageTaskName
            }
        }
        return null
    }

    private fun setupAndroidPlatformTask(
        module: PotatoModule,
        tasks: TaskGraphBuilder,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        androidSdkPath: Path,
        isTest: Boolean,
        platform: Platform,
    ): TaskName? {
        if (platform == Platform.ANDROID) {
            val testSuffix = if (isTest) "Test" else ""
            return module
                .fragments
                .filter { Platform.ANDROID in it.platforms }
                .firstOrNull { it.isTest == isTest }
                ?.let { androidFragment ->
                    val targetSdk = androidFragment.settings.android.targetSdk.versionNumber
                    val downloadAndroidSdkTaskName =
                        TaskName.fromHierarchy(listOf(module.userReadableName, "downloadSdkAndroid$testSuffix"))
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
                    downloadAndroidSdkTaskName
                }
        }
        return null
    }

    // All task binding between themselves happens here, so let's keep it private
    private enum class CommonTaskType {
        COMPILE,
        DEPENDENCIES,
        RUN,
        TEST,
    }

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
}
