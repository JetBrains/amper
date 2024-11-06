/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.core.useWithoutCoroutines
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.runTasksAndReportOnFailure
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.SchemaBasedModelImport
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.tasks.BuildTask
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.PublishTask
import org.jetbrains.amper.tasks.RunTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.TestTask
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.PlatformUtil
import org.jetbrains.annotations.TestOnly
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.pathString

class AmperBackend(val context: CliContext) {
    private val resolvedModel: Model by lazy {
        with(CliProblemReporterContext) {
            val model = spanBuilder("loading model")
                .setAttribute("root", context.projectRoot.path.pathString)
                .useWithoutCoroutines {
                    when (val result = SchemaBasedModelImport.getModel(context.projectContext)) {
                        is Result.Failure -> {
                            if (problemReporter.wereProblemsReported()) {
                                userReadableError("failed to build tasks graph, refer to the errors above")
                            }
                            else throw result.exception
                        }
                        is Result.Success -> result.value
                    }
                }

            if (problemReporter.wereProblemsReported()) {
                userReadableError("failed to build tasks graph, refer to the errors above")
            }

            for ((moduleUserReadableName, moduleList) in model.modules.groupBy { it.userReadableName }) {
                if (moduleList.size > 1) {
                    userReadableError("Module name '${moduleUserReadableName}' is not unique, it's declared in " +
                    moduleList.joinToString(" ") { (it.source as? AmperModuleFileSource)?.buildFile?.toString() ?: "" })
                }
            }

            model
        }
    }

    private val modulesByName by lazy {
        resolvedModel.modules.associateBy { it.userReadableName }
    }

    private val taskGraph: TaskGraph by lazy {
        ProjectTasksBuilder(context = context, model = resolvedModel).build()
    }

    private val taskExecutor: TaskExecutor by lazy {
        val progress = TaskProgressRenderer(context.terminal, context.backgroundScope)
        TaskExecutor(taskGraph, context.taskExecutionMode, progress)
    }

    /**
     * Called by the 'build' command. Compiles and links all code in the project.
     *
     * If [platforms] is specified, only compilation/linking for those platforms should be run.
     *
     * If [modules] is specified, only compilation/linking for those modules should be run.
     */
    suspend fun build(platforms: Set<Platform>? = null, modules: Set<String>? = null) {
        if (platforms != null) {
            logger.info("Compiling for platforms: ${platforms.map { it.name }.sorted().joinToString(" ")}")
        }
        if (modules != null) {
            logger.info("Compiling modules: ${modules.sorted().joinToString(" ")}")
        }

        val possibleCompilationPlatforms = if (OsFamily.current.isMac) {
            Platform.leafPlatforms
        } else {
            // Apple targets could be compiled only on Mac OS X due to legal obstacles
            Platform.leafPlatforms.filter { !it.isDescendantOf(Platform.APPLE) }.toSet()
        }

        val platformsToCompile = platforms ?: possibleCompilationPlatforms
        val modulesToCompile = (modules?.map { resolveModule(it) } ?: resolvedModel.modules).toSet()

        val taskNames = taskGraph
            .tasks
            .filterIsInstance<BuildTask>()
            .filter { it.platform in platformsToCompile && it.module in modulesToCompile }
            .map { it.taskName }
            .toSet()
        logger.info("Selected tasks to compile: ${taskNames.sortedBy { it.name }.joinToString(" ") { it.name }}")
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

    @TestOnly
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
                if (module.mavenRepositories.any { it.id == repositoryId }) {
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

        context.terminal.println("Tasks that will be executed:\n" +
            publishTasks.sorted().joinToString("\n"))

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

        runTask(task.taskName)
    }

    private fun resolveModule(moduleName: String) = modulesByName[moduleName] ?: userReadableError(
        "Unable to resolve module by name '$moduleName'.\n\n" +
                "Available modules: ${availableModulesString()}"
    )

    private fun availableModulesString() =
        resolvedModel.modules.map { it.userReadableName }.sorted().joinToString(" ")

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
}
