/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.slf4j.MDC

class TaskExecutor(
    private val graph: TaskGraph,
    private val mode: Mode,
    private val progressListener: TaskProgressListener = TaskProgressListener.Noop,
) {
    init {
        // verify all dependencies are resolved
        for ((taskName, dependsOn) in graph.dependencies) {
            if (!graph.nameToTask.containsKey(taskName)) {
                error("Task '$taskName' does not exist, yet it depends on ${dependsOn.map { it.name }.sorted().joinToString()}")
            }
            for (dependency in dependsOn) {
                if (!graph.nameToTask.containsKey(dependency)) {
                    error("Task '$taskName' depends on task '$dependency' which does not exist")
                }
            }
        }

        check(!graph.nameToTask.containsKey(rootTaskName)) {
            "task graph should not contain internal root task name '${rootTaskName.name}'"
        }
    }

    private val availableProcessors = Runtime.getRuntime().availableProcessors()

    @OptIn(ExperimentalCoroutinesApi::class)
    // TODO Should be configurable
    private val tasksDispatcher = Dispatchers.IO.limitedParallelism(availableProcessors.coerceAtLeast(3))

    // Dispatch on default dispatcher, execute on tasks dispatcher
    // Task failures do not throw, instead all exceptions are returned as a map
    suspend fun run(tasksToRun: Set<TaskName>): Map<TaskName, Result<TaskResult>> = withContext(Dispatchers.Default) {
        spanBuilder("Run tasks")
            .setAttribute("root-tasks", tasksToRun.joinToString { it.name })
            .use {
                tasksToRun.forEach { assertTaskIsKnown(it) }
                val results = mutableMapOf<TaskName, Deferred<Result<TaskResult>>>()
                val executionContext = DefaultTaskGraphExecutionContext()
                try {
                    coroutineScope {
                        runTask(rootTaskName, emptyList(), results, rootTaskDependencies = tasksToRun, executionContext)
                        results.mapValues { it.value.await() }
                    }
                } catch (e: CancellationException) {
                    results.mapValues { if (it.value.isCancelled) Result.failure(e) else it.value.await() }
                } finally {
                    spanBuilder("Post graph execution hooks").use {
                        executionContext.runPostGraphExecutionHooks()
                    }
                }
            }
    }

    private fun assertTaskIsKnown(taskName: TaskName) {
        if (taskName !in graph.nameToTask.keys) {
            val similarNames = findSimilarTaskNames(taskName).sorted()
            val extraInfo = if (similarNames.isEmpty()) "" else ", maybe you meant one of:\n${similarNames.joinToString("\n").prependIndent("   ")}"
            userReadableError("Task '${taskName.name}' was not found in the project$extraInfo")
        }
    }

    private fun findSimilarTaskNames(taskName: TaskName): List<String> =
        graph.nameToTask.keys
            .map { it.name }
            .filter { it.contains(taskName.name, ignoreCase = true) || taskName.name.contains(it, ignoreCase = true) }

    // TODO we need to re-evaluate task order execution later
    private suspend fun runTask(
        taskName: TaskName,
        currentPath: List<TaskName>,
        taskResults: MutableMap<TaskName, Deferred<Result<TaskResult>>>,
        rootTaskDependencies: Set<TaskName>,
        executionContext: TaskGraphExecutionContext,
    ): Result<TaskResult> = withContext(Dispatchers.Default) {
        fun taskDependencies(taskName: TaskName): Collection<TaskName> = when (taskName) {
            rootTaskName -> rootTaskDependencies
            else -> graph.dependencies[taskName] ?: emptySet()
        }

        suspend fun runTask(taskName: TaskName, dependenciesResult: List<TaskResult>): TaskResult = when (taskName) {
            rootTaskName -> object : TaskResult {}

            else -> {
                val task = graph.nameToTask[taskName] ?: error("Unable to find task by name: ${taskName.name}")
                progressListener.taskStarted(taskName).use {
                    MDC.put("amper-task-name", taskName.name)
                    withContext(MDCContext() + CoroutineName("task:${taskName.name}")) {
                        task.run(dependenciesResult, executionContext)
                    }
                }
            }
        }

        coroutineScope {
            val results = taskDependencies(taskName)
                .map { dependsOn ->
                    // TODO slow, we can do better for sure
                    if (currentPath.contains(dependsOn)) {
                        error("Found a cycle in task execution graph:\n" +
                                (currentPath + dependsOn).joinToString(" -> ") { it.name })
                    }
                    val newPath = currentPath + dependsOn

                    dependsOn to synchronized(taskResults) {
                        val existingResult = taskResults[dependsOn]
                        if (existingResult != null) {
                            existingResult
                        } else {
                            val newDeferred = async {
                                runTask(dependsOn, newPath, taskResults, rootTaskDependencies, executionContext)
                            }
                            taskResults[dependsOn] = newDeferred
                            newDeferred
                        }
                    }
                }
                .map { (dependsOn, deferredResult) ->
                    withContext(CoroutineName("task:${taskName.name} waiting for ${dependsOn.name}")) {
                        dependsOn to deferredResult.await()
                    }
                }
                .map { (dependsOn, result) ->
                    result.getOrElse { ex ->
                        // terminate task execution since dependency failed
                        return@coroutineScope Result.failure(CancellationException("task dependency '$dependsOn' failed", ex))
                    }
                }

            withContext(tasksDispatcher) {
                spanBuilder("task ${taskName.name}")
                    .use {
                        val result = runCatching {
                            runTask(taskName, results)
                        }

                        when (mode) {
                            Mode.GREEDY -> result
                            Mode.FAIL_FAST -> if (result.isFailure) {
                                taskError(taskName, result.exceptionOrNull()!!)
                            } else result
                        }
                    }
            }
        }
    }

    enum class Mode {
        /**
         * Upon task failure continue execution of all other tasks,
         * that are independent of the failed task in the task graph
         */
        GREEDY,

        /**
         * Fail on a first failed task, cancel all running and queued tasks upon failure
         */
        FAIL_FAST,
    }

    private fun taskError(taskName: TaskName, cause: Throwable): Nothing {
        when(cause) {
            is UserReadableError -> userReadableError("Task '${taskName.name}' failed: ${cause.message}", cause.exitCode)
            is CancellationException -> throw cause  // Cooperate to cancellation
            else -> throw TaskExecutionFailed(taskName, cause)
        }
    }

    class TaskExecutionFailed(val taskName: TaskName, val exception: Throwable)
        : Exception("Task '${taskName.name}' failed: $exception", exception)

    companion object {
        private val rootTaskName = TaskName(":")
    }
}
