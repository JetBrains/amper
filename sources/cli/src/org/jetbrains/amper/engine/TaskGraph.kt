/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import org.jetbrains.amper.cli.userReadableError
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

/**
 * @param roots graph nodes from which all the graph is reachable.
 * @param adjacent the function that defines the nodes that are adjacent to the given node (linked by edges)
 */
private fun <T : Any> depthFirstDetectLoops(
    roots: Iterable<T>,
    adjacent: (T) -> Iterable<T>,
): List<List<T>> {
    val loops = mutableListOf<List<T>>()

    val markedGray = hashSetOf<T>()
    val markedBlack = hashSetOf<T>()
    val stack = arrayListOf<MutableList<T>>()

    // dynamic, because it's a sequence
    val currentPath = stack.asSequence().map { it.last() }

    stack += roots.toMutableList()

    while (stack.isNotEmpty()) {
        // Substack is introduced to preserve node hierarchy
        val subStack = stack.last()
        if (subStack.isEmpty()) {
            stack.removeLast()
            continue
        }

        when (val node = subStack.last()) {
            in markedBlack -> {
                subStack.removeLast()
            }

            in markedGray -> {
                subStack.removeLast()
                markedBlack += node
                markedGray -= node
            }

            else -> {
                markedGray += node
                stack += adjacent(node).mapNotNullTo(arrayListOf()) { child ->
                    if (child in markedGray) {
                        // Loop: report and skip
                        loops += currentPath.dropWhile { it != child }.toList()
                        null
                    } else child
                }
            }
        }
    }
    return loops
}
