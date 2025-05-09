/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.depthFirstDetectLoops
import org.jetbrains.amper.frontend.TaskName

class TaskGraph(
    val nameToTask: Map<TaskName, Task>,
    val dependencies: Map<TaskName, Set<TaskName>>,
) {
    val tasks = nameToTask.values

    init {
        // verify all dependencies are resolved
        for ((name, dependsOn) in dependencies) {
            if (!nameToTask.containsKey(name)) {
                userReadableError("Task '$name' does not exist, yet it depends on ${dependsOn.map { it.name }.sorted().joinToString()}")
            }
            for (dependency in dependsOn) {
                if (!nameToTask.containsKey(dependency)) {
                    userReadableError("Task '$name' depends on task '$dependency' which does not exist")
                }
            }
        }

        // verify no dependency loops
        val allTasks = nameToTask.keys.distinct()
        val loops = depthFirstDetectLoops(
            roots = allTasks,
            adjacent = { taskName -> dependencies[taskName].orEmpty() },
        )
        if (loops.isNotEmpty()) {
            val loopDescriptions = loops.map { loop ->
                buildString {
                    var indent = 0
                    append(loop.last().name)
                    loop.forEach { taskName ->
                        appendLine()
                        repeat(indent) { append(' ') }
                        append("╰> ")
                        append(taskName.name)
                        indent += 3
                    }
                }
            }
            if (loopDescriptions.isNotEmpty()) {
                userReadableError(
                    "Task dependency ${if (loopDescriptions.size == 1) "loop is" else "loops are"} detected:\n" +
                            loopDescriptions.joinToString(separator = "\n\n")
                )
            }
        }
    }
}