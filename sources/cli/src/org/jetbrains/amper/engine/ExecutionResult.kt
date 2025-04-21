/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult

sealed interface ExecutionResult {

    /**
     * The name of the task that was executed.
     */
    val taskName: TaskName

    sealed interface Unsuccessful : ExecutionResult {
        val transitiveFailures: Set<Failure>
    }

    /**
     * The result of a task that ran to completion and returned a [TaskResult].
     */
    data class Success(override val taskName: TaskName, val result: TaskResult) : ExecutionResult

    /**
     * The result of a task that failed with an exception.
     */
    data class Failure(override val taskName: TaskName, val exception: Throwable) : Unsuccessful {
        override val transitiveFailures: Set<Failure> = setOf(this)
    }

    /**
     * The result of a task that was not executed because at least one of its dependencies (or transitive dependencies)
     * failed with an exception.
     */
    data class DependencyFailed(
        override val taskName: TaskName,
        val unsuccessfulDependencies: Set<Unsuccessful>,
    ) : Unsuccessful {
        override val transitiveFailures: Set<Failure> by lazy {
            unsuccessfulDependencies.flatMapTo(mutableSetOf()) {
                when (it) {
                    is Failure -> setOf(it)
                    is DependencyFailed -> it.transitiveFailures
                }
            }
        }
    }
}
