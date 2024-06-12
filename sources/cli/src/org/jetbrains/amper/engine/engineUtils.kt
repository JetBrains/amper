/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import org.jetbrains.amper.frontend.TaskName

suspend fun TaskExecutor.runTasksAndReportOnFailure(tasks: Set<TaskName>) {
    val result = run(tasks)
    val exceptions = tasks
        .map { taskName -> result.getValue(taskName) }
        .mapNotNull { it.exceptionOrNull() }
    if (exceptions.isNotEmpty()) {
        val firstException = exceptions.first()
        exceptions.drop(1).forEach { e -> firstException.addSuppressed(e) }
        throw firstException
    }
}
