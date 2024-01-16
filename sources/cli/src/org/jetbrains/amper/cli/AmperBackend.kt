/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.use
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.resolve.resolved
import org.jetbrains.amper.tasks.CompileTask
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.RunTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.pathString

class AmperBackend(val context: ProjectContext) {
    private val resolvedModel: Model by lazy {
        with(CliProblemReporterContext()) {
            val model = spanBuilder("loading model")
                .setAttribute("root", context.projectRoot.path.pathString)
                .startSpan().use {
                    when (val result = ModelInit.getModel(context.projectRoot.path)) {
                        is Result.Failure -> throw result.exception
                        is Result.Success -> result.value
                    }
                }

            val resolved = model.resolved

            if (problemReporter.wereProblemsReported()) {
                userReadableError("failed to build tasks graph, refer to the errors above")
            }

            resolved
        }
    }

    private val taskGraph: TaskGraph by lazy {
        ProjectTasksBuilder(context = context, model = resolvedModel).build()
    }

    private val taskExecutor: TaskExecutor by lazy {
        TaskExecutor(taskGraph)
    }

    fun compile(platforms: Set<Platform>? = null) {
        if (platforms != null) {
            logger.info("Compiling for platforms: ${platforms.map { it.name }.sorted().joinToString(" ")}")
        }

        val platformsToCompile: Set<Platform> = platforms ?: Platform.leafPlatforms
        runBlocking {
            val taskNames = taskGraph
                .tasks
                .filterIsInstance<CompileTask>()
                .filter { platformsToCompile.contains(it.platform) }
                .map { it.taskName }
                .sortedBy { it.name }
            logger.info("Selected tasks to compile: ${taskNames.joinToString(" ") { it.name }}")
            taskExecutor.run(taskNames)
        }
    }

    fun runTask(taskName: TaskName) {
        runBlocking {
            taskExecutor.run(listOf(taskName))
        }
    }

    fun showTasks() {
        for (taskName in taskGraph.tasks.map { it.taskName }.sortedBy { it.name }) {
            print("task ${taskName.name}")
            if (taskGraph.dependencies.containsKey(taskName)) {
                print(" -> ${taskGraph.dependencies[taskName]!!.joinToString { it.name }}")
            }
            println()
        }
    }

    fun check(moduleName: String?, platforms: Set<Platform>?) {
        require(platforms == null || platforms.isNotEmpty())

        val modulesToCheck: List<PotatoModule> = if (moduleName != null) {
            val oneModule = (resolvedModel.modules.firstOrNull { it.userReadableName == moduleName }
                ?: userReadableError("Unable to resolve module by name '$moduleName'.\n\nAvailable modules: ${availableModulesString()}"))
            listOf(oneModule)
        } else {
            resolvedModel.modules
        }






    }

    fun run(moduleName: String?, platform: Platform?) {
        val moduleToRun = if (moduleName != null) {
            resolvedModel.modules.firstOrNull { it.userReadableName == moduleName }
                ?: userReadableError("Unable to resolve module by name '$moduleName'.\n\nAvailable modules: ${availableModulesString()}")
        } else {
            val candidates = resolvedModel.modules.filter { it.type.isApplication() }
            when {
                candidates.isEmpty() -> userReadableError("No modules in the project with application type")
                candidates.size > 1 ->
                    userReadableError(
                        "There are several application modules in the project. Please specify one with '--module' argument.\n\n" +
                                "Available modules: ${availableModulesString()}"
                    )

                else -> candidates.single()
            }
        }

        val moduleRunTasks = taskGraph.tasks.filterIsInstance<RunTask>().filter { it.module == moduleToRun }
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

    private fun availableModulesString() =
        resolvedModel.modules.map { it.userReadableName }.sorted().joinToString(" ")

    private val logger: Logger = LoggerFactory.getLogger(javaClass)
}
