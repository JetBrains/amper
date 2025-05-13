/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.slf4j.MDC
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class TaskExecutor(
    private val graph: TaskGraph,
    private val mode: Mode,
    private val progressListener: TaskProgressListener = TaskProgressListener.Noop,
) {
    private val availableProcessors = Runtime.getRuntime().availableProcessors()

    @OptIn(ExperimentalCoroutinesApi::class)
    // TODO Should be configurable
    private val tasksDispatcher = Dispatchers.IO.limitedParallelism(availableProcessors.coerceAtLeast(3))

    /**
     * Runs the given set of tasks, and returns the result of all tasks that were executed, including dependencies.
     * Use the [mode] on this [TaskExecutor] to choose whether to fail fast or keep executing as many as as possible in
     * case of failure.
     *
     * @throws TaskExecutionFailed if any task fails with a non-[UserReadableError] exception in
     * [FAIL_FAST][Mode.FAIL_FAST] mode.
     * @throws UserReadableError if any of the given [tasksToRun] is not found in the current task graph, or if a task
     * fails with a [UserReadableError] in [FAIL_FAST][Mode.FAIL_FAST] mode.
     */
    // Dispatch on default dispatcher, execute on tasks dispatcher
    suspend fun run(tasksToRun: Set<TaskName>): Map<TaskName, ExecutionResult> = withContext(Dispatchers.Default) {
        spanBuilder("Run tasks")
            .setAttribute("root-tasks", tasksToRun.joinToString { it.name })
            .use {
                tasksToRun.forEach { assertTaskIsKnown(it) }
                val executionContext = DefaultTaskGraphExecutionContext()
                try {
                    val results = ConcurrentHashMap<TaskName, Deferred<ExecutionResult>>()
                    runTasks(tasksToRun, currentPath = emptyList(), results, executionContext)

                    // this is just to unpack results (by that point, all tasks must have finished executing already)
                    results.mapValues { it.value.await() }
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

    /**
     * Runs the tasks identified by [taskNames], and returns their results.
     * If one of the given tasks is already running, it will be awaited instead of starting a new coroutine.
     *
     * Fails if one of the given [taskNames] is already in the graph execution's [currentPath] (which means a cycle).
     */
    private suspend fun runTasks(
        taskNames: Set<TaskName>,
        currentPath: List<TaskName>,
        taskResults: ConcurrentMap<TaskName, Deferred<ExecutionResult>>,
        executionContext: TaskGraphExecutionContext,
    ): List<ExecutionResult> = coroutineScope {
        taskNames
            .map { taskName ->
                // we need to check for cycles here, otherwise we'll await the existing Deferred here and deadlock
                if (currentPath.contains(taskName)) {
                    val formattedCycle = (currentPath + taskName).joinToString("\n -> ") { it.name }
                    error("Found a cycle in task execution graph:\n$formattedCycle")
                }
                taskResults.computeIfAbsent(taskName) {
                    async { runDependenciesAndTask(taskName, currentPath = currentPath, taskResults, executionContext) }
                }
            }
            .awaitAll() // Note: we might be awaiting async coroutines from other scopes here
    }

    /**
     * Runs the given task's dependencies, and then the task itself.
     */
    private suspend fun runDependenciesAndTask(
        taskName: TaskName,
        currentPath: List<TaskName>,
        taskResults: ConcurrentMap<TaskName, Deferred<ExecutionResult>>,
        executionContext: TaskGraphExecutionContext,
    ): ExecutionResult {
        val taskDependencies = graph.dependencies[taskName] ?: emptySet()
        val dependencyResults = runTasks(taskDependencies, currentPath + taskName, taskResults, executionContext)
        val (successful, unsuccessful) = dependencyResults.partitionDependencyResults()
        if (unsuccessful.isNotEmpty()) {
            // skip task execution since at least one dependency failed
            return ExecutionResult.DependencyFailed(taskName, unsuccessfulDependencies = unsuccessful)
        }
        return runSingleTaskSafely(taskName, successful, executionContext)
    }

    private fun List<ExecutionResult>.partitionDependencyResults(): DependencyResults {
        // we don't use the stdlib's partition() here because we want to be type-safe and avoid casts
        val successful = mutableListOf<TaskResult>()
        val unsuccessful = mutableSetOf<ExecutionResult.Unsuccessful>()
        forEach {
            when (it) {
                is ExecutionResult.Success -> successful.add(it.result)
                is ExecutionResult.Unsuccessful -> unsuccessful.add(it)
            }
        }
        return DependencyResults(successful = successful, unsuccessful = unsuccessful)
    }

    private data class DependencyResults(
        val successful: List<TaskResult>,
        val unsuccessful: Set<ExecutionResult.Unsuccessful>,
    )

    /**
     * Runs the task identified by [taskName], and returns its result.
     */
    private suspend fun runSingleTaskSafely(
        taskName: TaskName,
        dependencyResults: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): ExecutionResult = try {
        val taskResult = runSingleTask(taskName, dependencyResults, executionContext)
        ExecutionResult.Success(taskName, taskResult)
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive() // cooperate with cancellations
        when (mode) {
            Mode.GREEDY -> ExecutionResult.Failure(taskName, e)
            Mode.FAIL_FAST -> when (e) {
                is UserReadableError -> userReadableError("Task '${taskName.name}' failed: ${e.message}", e.exitCode)
                else -> throw TaskExecutionFailed(taskName, e)
            }
        }
    }

    private suspend fun runSingleTask(
        taskName: TaskName,
        dependencyResults: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult = spanBuilder("task ${taskName.name}").use {
        progressListener.taskStarted(taskName).use {
            val task = graph.nameToTask[taskName] ?: error("Unable to find task by name: ${taskName.name}")
            MDC.put("amper-task-name", taskName.name)
            withContext(tasksDispatcher + MDCContext() + CoroutineName("task:${taskName.name}")) {
                task.run(dependencyResults, executionContext)
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

    class TaskExecutionFailed(val taskName: TaskName, val exception: Throwable)
        : Exception("Task '${taskName.name}' failed: $exception", exception)
}
