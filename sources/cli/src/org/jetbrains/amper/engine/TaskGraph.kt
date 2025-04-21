/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import org.jetbrains.amper.frontend.TaskName
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

class TaskGraph(
    val nameToTask: Map<TaskName, Task>,
    val dependencies: Map<TaskName, Set<TaskName>>,
) {
    val tasks = nameToTask.values

    init {
        // verify all dependencies are resolved
        for ((name, dependsOn) in dependencies) {
            if (!nameToTask.containsKey(name)) {
                error("Task '$name' does not exist, yet it depends on ${dependsOn.map { it.name }.sorted().joinToString()}")
            }
            for (dependency in dependsOn) {
                if (!nameToTask.containsKey(dependency)) {
                    error("Task '$name' depends on task '$dependency' which does not exist")
                }
            }
        }
    }
}
