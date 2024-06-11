/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

interface TaskResult {
    val dependencies: List<TaskResult>
}

open class BaseTaskResult(
    override val dependencies: List<TaskResult> = emptyList()
) : TaskResult

object EmptyTaskResult : BaseTaskResult()

/**
 * Returns a sequence of all these results and their dependencies recursively, in an unspecified order.
 */
fun Iterable<TaskResult>.walkRecursively(): Sequence<TaskResult> =
    asSequence().flatMap { it.walkDependenciesRecursively() + it }

/**
 * Returns a sequence of this [TaskResult]'s dependencies and their transitive dependencies, in an unspecified order.
 */
private fun TaskResult.walkDependenciesRecursively(): Sequence<TaskResult> = sequence {
    dependencies.forEach {
        yield(it)
        yieldAll(it.walkDependenciesRecursively())
    }
}
