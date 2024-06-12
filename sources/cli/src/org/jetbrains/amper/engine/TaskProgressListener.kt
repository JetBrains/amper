/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import org.jetbrains.amper.frontend.TaskName

interface TaskProgressListener {
    fun taskStarted(taskName: TaskName): TaskProgressCookie
    interface TaskProgressCookie: AutoCloseable

    object Noop: TaskProgressListener {
        override fun taskStarted(taskName: TaskName): TaskProgressCookie = object : TaskProgressCookie {
            override fun close() = Unit
        }
    }
}
