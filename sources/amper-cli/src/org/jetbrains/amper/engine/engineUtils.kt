/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.engine.TaskExecutor.TaskExecutionFailed
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult

/**
 * Runs the given set of [tasks] and their dependencies, and throws an exception if any task fails.
 * If all tasks are successful, the results of all tasks that were executed are returned as a map, including results
 * of the task dependencies.
 *
 * Use the [mode][TaskExecutor.mode] on this [TaskExecutor] to choose whether to fail fast or keep executing as many
 * tasks as possible in case of failure.
 *
 * @throws TaskExecutionFailed if any task fails with a non-[UserReadableError] exception.
 * @throws UserReadableError if any of the given [tasks] is not found in the current task graph, or if a task
 * fails with a [UserReadableError].
 */
suspend fun TaskExecutor.runTasksAndReportOnFailure(tasks: Set<TaskName>): Map<TaskName, TaskResult> {
    val result = run(tasks) // this directly throws in fail-fast mode
    return result.resultsOrThrowCombinedError()
}

/**
 * Returns a map of successful task results, or throws an exception if any task failed. In case of failure, the first
 * task exception is rethrown, and all other task failures are provided as suppressed exceptions.
 */
private fun Map<TaskName, ExecutionResult>.resultsOrThrowCombinedError(): Map<TaskName, TaskResult> {
    val exceptions = values.filterIsInstance<ExecutionResult.Failure>().map { it.exception }
    if (exceptions.isNotEmpty()) {
        val firstException = exceptions.first()
        exceptions.drop(1).forEach { e -> firstException.addSuppressed(e) }
        throw firstException
    }
    return mapValues { (_, result) ->
        (result as? ExecutionResult.Success)?.result
            ?: error("All tasks should be successful here, because we throw in case of failure")
    }
}
