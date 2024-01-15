/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

class TaskGraph(val nameToTask: Map<TaskName, Task>,
                val dependencies: Map<TaskName, Set<TaskName>>) {
    val tasks = nameToTask.values
}
