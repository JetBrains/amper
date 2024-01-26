/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.amper.tasks.TaskResult

class TaskExecutor(private val graph: TaskGraph) {
    init {
        // verify all dependencies are resolved
        for ((taskName, dependsOn) in graph.dependencies) {
            if (!graph.nameToTask.containsKey(taskName)) {
                error("Task '$taskName' does not exist, yet it defines dependencies")
            }
            for (dependency in dependsOn) {
                if (!graph.nameToTask.containsKey(dependency)) {
                    error("Task '$taskName' depends on task '$dependency' which does not exist")
                }
            }
        }
    }

    // TODO Should be configurable
    @OptIn(ExperimentalCoroutinesApi::class)
    private val tasksDispatcher = Dispatchers.IO.limitedParallelism(5)

    // Dispatch on default dispatcher, execute on tasks dispatcher
    suspend fun run(tasks: List<TaskName>) = withContext(Dispatchers.Default) {
        for (task in tasks) {
            runTask(task, emptyList())
        }
    }

    // TODO we need to re-evaluate task order execution later
    private suspend fun runTask(taskName: TaskName, currentPath: List<TaskName>): TaskResult = withContext(Dispatchers.Default) {
        // TODO slow, we can do better for sure
        if (currentPath.contains(taskName)) {
            error("Found a cycle in task execution graph:\n" +
                    (currentPath + taskName).joinToString(" -> ") { it.name })
        }
        val newPath = currentPath + taskName

        coroutineScope {
            val results = (graph.dependencies[taskName] ?: emptySet())
                .map { dependsOn -> async { runTask(dependsOn, newPath) } }
                .map { it.await() }

            withContext(tasksDispatcher) {
                val task = graph.nameToTask[taskName] ?: error("Unable to find task by name: ${taskName.name}")
                task.run(results)
            }
        }
    }
}
