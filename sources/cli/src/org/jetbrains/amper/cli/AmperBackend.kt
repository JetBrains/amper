/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.cli.commands.UserJvmArgsOption
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.engine.BuildTask
import org.jetbrains.amper.engine.PackageTask
import org.jetbrains.amper.engine.RunTask
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.TestTask
import org.jetbrains.amper.engine.runTasksAndReportOnFailure
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.SchemaBasedModelImport
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.PublishTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.ios.IosTaskType
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.PlatformUtil
import org.jetbrains.annotations.TestOnly
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.pathString

class AmperBackend(val context: CliContext) {
    private val resolvedModel: Model = with(CliProblemReporterContext) {
        val model = spanBuilder("Read model from Amper files")
            .setAttribute("root", context.projectRoot.path.pathString)
            .useWithoutCoroutines {
                when (val result = SchemaBasedModelImport.getModel(context.projectContext)) {
                    is Result.Failure -> {
                        if (problemReporter.wereProblemsReported()) {
                            userReadableError("failed to read Amper model, refer to the errors above")
                        }
                        else throw result.exception
                    }
                    is Result.Success -> result.value
                }
            }

        if (problemReporter.wereProblemsReported()) {
            userReadableError("failed to read Amper model, refer to the errors above")
        }

        for ((moduleUserReadableName, moduleList) in model.modules.groupBy { it.userReadableName }) {
            if (moduleList.size > 1) {
                userReadableError("Module name '${moduleUserReadableName}' is not unique, it's declared in " +
                moduleList.joinToString(" ") { (it.source as? AmperModuleFileSource)?.buildFile?.toString() ?: "" })
            }
        }

        model
    }

    private val modulesByName by lazy {
        resolvedModel.modules.associateBy { it.userReadableName }
    }

    private val taskGraph: TaskGraph by lazy {
        spanBuilder("Build task graph").useWithoutCoroutines {
            ProjectTasksBuilder(context = context, model = resolvedModel).build()
        }
    }

    private val taskExecutor: TaskExecutor by lazy {
        val progress = TaskProgressRenderer(context.terminal, context.backgroundScope)
        TaskExecutor(taskGraph, context.taskExecutionMode, progress)
    }

    /**
     * Called by the 'build' command.
     * Builds ready-to-run and ready-to-test artifacts for all included modules/platforms/buildTypes.
     *
     * The idea is that `amper run` and `amper test`
     * (if called with compatible filters) will practically do no building work after `amper build`.
     *
     * If [platforms] is specified, only compilation/linking for those platforms should be run.
     *
     * If [modules] is specified, only compilation/linking for those modules should be run.
     *
     * If [buildTypes] are specified, only compilation/linking
     */
    suspend fun build(
        platforms: Set<Platform>? = null,
        modules: Set<String>? = null,
        buildTypes: Set<BuildType>? = null,
    ) {
        if (platforms != null) {
            logger.info("Compiling for platforms: ${platforms.map { it.name }.sorted().joinToString(" ")}")
        }
        if (modules != null) {
            logger.info("Compiling modules: ${modules.sorted().joinToString(" ")}")
        }
        if (buildTypes != null) {
            logger.info("Compiling variants: ${buildTypes.map { it.value }.sorted().joinToString(" ")}")
        }

        val possibleCompilationPlatforms = if (OsFamily.current.isMac) {
            Platform.leafPlatforms
        } else {
            // Apple targets could be compiled only on Mac OS X due to legal obstacles
            Platform.leafPlatforms.filter { !it.isDescendantOf(Platform.APPLE) }.toSet()
        }

        val platformsToCompile = platforms ?: possibleCompilationPlatforms
        val modulesToCompile = (modules?.map { resolveModule(it) } ?: resolvedModel.modules).toSet()
        val buildTypesToCompile = buildTypes ?: BuildType.entries.toSet()

        val taskNames = taskGraph
            .tasks
            .filterIsInstance<BuildTask>()
            .filter {
                it.platform in platformsToCompile &&
                        it.module in modulesToCompile &&
                        (it.buildType == null || it.buildType in buildTypesToCompile)
            }
            .map { it.taskName }
            .toSet()
        logger.debug("Selected tasks to compile: ${formatTaskNames(taskNames)}")
        taskExecutor.runTasksAndReportOnFailure(taskNames)
    }

    /**
     * Called by the 'package' command.
     * Packages artifacts for distribution for all included modules/platforms/buildTypes.
     *
     * If [platforms] is specified, only packaging for those platforms should be run.
     *
     * If [modules] is specified, only packaging for those modules should be run.
     *
     * If [buildTypes] are specified, only packaging for those build types should be run.
     *
     * If [formats] is specified, only packaging in those formats should be run.
     */
    suspend fun `package`(
        platforms: Set<Platform>? = null,
        modules: Set<String>? = null,
        buildTypes: Set<BuildType>? = null,
        formats: Set<PackageTask.Format>? = null,
    ) {
        if (platforms != null) {
            logger.info("Packaging for platforms: ${platforms.map { it.name }.sorted().joinToString(" ")}")
        }
        if (modules != null) {
            logger.info("Packaging modules: ${modules.sorted().joinToString(" ")}")
        }
        if (buildTypes != null) {
            logger.info("Packaging variants: ${buildTypes.map { it.value }.sorted().joinToString(" ")}")
        }
        if (formats != null) {
            logger.info("Packaging formats: ${formats.map { it.value }.sorted().joinToString(" ")}")
        }

        val possiblePlatforms = if (OsFamily.current.isMac) {
            Platform.leafPlatforms
        } else {
            // Apple targets could be packaged only on Mac OS X due to legal obstacles
            Platform.leafPlatforms.filter { !it.isDescendantOf(Platform.APPLE) }.toSet()
        }

        val platformsToPackage = platforms ?: possiblePlatforms
        val modulesToPackage = (modules?.map { resolveModule(it) } ?: modules()).toSet()
        val buildTypesToPackage = buildTypes ?: BuildType.entries.toSet()
        val formatsToPackage = formats ?: PackageTask.Format.entries.toSet()

        val taskNames = taskGraph
            .tasks
            .filterIsInstance<PackageTask>()
            .filter {
                it.module in modulesToPackage &&
                it.platform in platformsToPackage &&
                it.format in formatsToPackage &&
                it.buildType in buildTypesToPackage
            }
            .map { it.taskName }
            .toSet()

        if (taskNames.isEmpty()) {
            userReadableError("No package tasks were found")
        }

        logger.debug("Selected tasks to package: ${formatTaskNames(taskNames)}")
        taskExecutor.runTasksAndReportOnFailure(taskNames)
    }

    suspend fun runTask(taskName: TaskName): kotlin.Result<TaskResult>? = taskExecutor.runTasksAndReportOnFailure(setOf(taskName))[taskName]

    @TestOnly
    fun tasks() = taskGraph.tasks.toList()

    fun showTasks() {
        for (taskName in taskGraph.tasks.map { it.taskName }.sortedBy { it.name }) {
            context.terminal.println(buildString {
                append("task ${taskName.name}")
                taskGraph.dependencies[taskName]?.let { taskDeps ->
                    append(" -> ${taskDeps.joinToString { it.name }}")
                }
            })
        }
    }

    fun modules(): List<AmperModule> = resolvedModel.modules

    fun showModules() {
        for (moduleName in resolvedModel.modules.map { it.userReadableName }.sorted()) {
            context.terminal.println(moduleName)
        }
    }

    suspend fun publish(modules: Set<String>?, repositoryId: String) {
        require(modules == null || modules.isNotEmpty())

        if (modules != null) {
            for (moduleName in modules) {
                val module = resolveModule(moduleName)
                if (module.mavenRepositories.none { it.id == repositoryId }) {
                    userReadableError("Module '$moduleName' does not have repository with id '$repositoryId'")
                }
            }
        }

        val publishTasks = taskGraph.tasks
            .filterIsInstance<PublishTask>()
            .filter { it.targetRepository.id == repositoryId }
            .filter { modules == null || modules.contains(it.module.userReadableName) }
            .map { it.taskName }
            .toSet()

        if (publishTasks.isEmpty()) {
            userReadableError("No publish tasks were found for specified module and repository filters")
        }

        logger.debug("Selected tasks to publish: ${formatTaskNames(publishTasks)}")
        taskExecutor.runTasksAndReportOnFailure(publishTasks)
    }

    suspend fun test(
        includeModules: Set<String>? = null,
        requestedPlatforms: Set<Platform>? = null,
        excludeModules: Set<String> = emptySet(),
    ) {
        require(requestedPlatforms == null || requestedPlatforms.isNotEmpty())

        val moduleNamesToCheck = (includeModules ?: emptySet()) + excludeModules
        moduleNamesToCheck.forEach { resolveModule(it) }

        requestedPlatforms
            ?.filter { it !in PlatformUtil.platformsMayRunOnCurrentSystem }
            ?.takeIf { it.isNotEmpty() }
            ?.let { unsupportedPlatforms ->
                fun format(platforms: Collection<Platform>) =
                    platforms.map { it.pretty }.sorted().joinToString(" ")
                val message = """
                    Unable to run requested platform(s) on the current system.
                    
                    Requested unsupported platforms: ${format(unsupportedPlatforms)}
                    Runnable platforms on the current system: ${format(PlatformUtil.platformsMayRunOnCurrentSystem)}
                """.trimIndent()
                userReadableError(message)
            }

        val allTestTasks = taskGraph.tasks.filterIsInstance<TestTask>()
        if (allTestTasks.isEmpty()) {
            userReadableError("No test tasks were found in the entire project")
        }

        val platformTestTasks = allTestTasks
            .filter { it.platform in (requestedPlatforms ?: PlatformUtil.platformsMayRunOnCurrentSystem) }
        requestedPlatforms?.filter { requestedPlatform ->
            platformTestTasks.none { it.platform == requestedPlatform }
        }?.takeIf { it.isNotEmpty() }?.let { platformsWithMissingTests ->
            userReadableError("No test tasks were found for platforms: " +
                    platformsWithMissingTests.map { it.name }.sorted().joinToString(" ")
            )
        }

        val includedTestTasks = if (includeModules != null) {
            platformTestTasks.filter { task -> includeModules.contains(task.module.userReadableName) }
        } else {
            platformTestTasks
        }
        if (includedTestTasks.isEmpty()) {
            userReadableError("No test tasks were found for specified include filters")
        }
        if (context.commonRunSettings.userJvmArgs.isNotEmpty() &&
            includedTestTasks.none { it.platform in setOf(Platform.JVM, Platform.ANDROID) }
        ) {
            logger.warn("The $UserJvmArgsOption option has no effect when running only non-JVM tests")
        }

        val testTasks = includedTestTasks
            .filter { task -> !excludeModules.contains(task.module.userReadableName) }
            .map { it.taskName }
            .toSet()
        if (testTasks.isEmpty()) {
            userReadableError("No test tasks were found after applying exclude filters")
        }

        taskExecutor.runTasksAndReportOnFailure(testTasks)
    }

    suspend fun runApplication(moduleName: String? = null, platform: Platform? = null, buildType: BuildType? = BuildType.Debug) {
        val moduleToRun = if (moduleName != null) {
            resolveModule(moduleName)
        } else {
            val candidates = resolvedModel.modules.filter { it.type.isApplication() }
            when {
                candidates.isEmpty() -> userReadableError("No modules in the project with application type")
                candidates.size > 1 ->
                    userReadableError(
                        "There are several application modules in the project. Please specify one with '--module' argument.\n\n" +
                                "Available application modules: ${candidates.map { it.userReadableName }.sorted()}"
                    )

                else -> candidates.single()
            }
        }

        val moduleRunTasks = taskGraph.tasks.filterIsInstance<RunTask>()
            .filter { it.module == moduleToRun }
            .filter { it.buildType == buildType }
        if (moduleRunTasks.isEmpty()) {
            userReadableError("No run tasks are available for module '${moduleToRun.userReadableName}'")
        }

        fun availablePlatformsForModule() = moduleRunTasks.map { it.platform.pretty }.sorted().joinToString(" ")

        val task: RunTask = if (platform == null) {
            if (moduleRunTasks.size == 1) {
                moduleRunTasks.single()
            } else {
                userReadableError("""
                    Multiple platforms are available to run in module '${moduleToRun.userReadableName}'.
                    Please specify one with '--platform' argument.

                    Available platforms: ${availablePlatformsForModule()}
                """.trimIndent())
            }
        } else {
            moduleRunTasks.firstOrNull { it.platform == platform }
                ?: userReadableError("""
                    Platform '${platform.pretty}' is not found for module '${moduleToRun.userReadableName}'.

                    Available platforms: ${availablePlatformsForModule()}
                """.trimIndent())
        }

        if (context.commonRunSettings.userJvmArgs.isNotEmpty() && task.platform != Platform.JVM) {
            logger.warn("The $UserJvmArgsOption option have no effect when running a non-JVM app")
        }
        if (context.commonRunSettings.deviceId != null &&
            !task.platform.isDescendantOf(Platform.IOS) && task.platform != Platform.ANDROID) {
            userReadableError("-d/--device-id argument is not supported for the ${task.platform.pretty} platform")
        }
        runTask(task.taskName)
    }

    /**
     * Run the iOS pre-build task identified by the [platform], [buildType] and the module.
     * The module is identified by its [moduleDir].
     *
     * @return the name of the module with the [moduleDir].
     */
    suspend fun prebuildForXcode(
        moduleDir: Path,
        platform: Platform,
        buildType: BuildType,
    ): String {
        val module = resolvedModel.modules.find { it.source.moduleDir == moduleDir }
        requireNotNull(module) {
            "Unable to resolve a module with the module directory '$moduleDir'"
        }

        if (platform !in module.leafPlatforms) {
            val availablePlatformsForModule = module.leafPlatforms.sorted().joinToString(" ")
            userReadableError("""
                    Platform '${platform.pretty}' is not found for iOS module '${module.userReadableName}'.
                    The module has declared platforms: $availablePlatformsForModule.
                    Please declare the required platform explicitly in the module's file.
                """.trimIndent())
        }

        val taskName = IosTaskType.PreBuildIosApp.getTaskName(
            module = module,
            platform = platform,
            isTest = false,
            buildType = buildType,
        )
        taskExecutor.runTasksAndReportOnFailure(setOf(taskName))

        return module.userReadableName
    }

    private fun resolveModule(moduleName: String) = modulesByName[moduleName] ?: userReadableError(
        "Unable to resolve module by name '$moduleName'.\n\n" +
                "Available modules: ${availableModulesString()}"
    )

    private fun availableModulesString() =
        resolvedModel.modules.map { it.userReadableName }.sorted().joinToString(" ")

    private fun formatTaskNames(publishTasks: Collection<TaskName>) =
        publishTasks.map { it.name }.sorted().joinToString(" ")

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
}
