/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult

interface Task {
    val taskName: TaskName
    suspend fun run(dependenciesResult: List<TaskResult>): TaskResult
}
