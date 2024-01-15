/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.TaskName

class TaskGraphBuilder {
    // do not give access to the graph while it's being built
    private val taskRegistry = mutableMapOf<TaskName, Task>()
    private val dependencies = mutableMapOf<TaskName, Set<TaskName>>()

    fun registerTask(task: Task, dependsOn: List<TaskName> = emptyList()) {
        if (taskRegistry.contains(task.taskName)) {
            error("Task '${task.taskName}' already exists")
        }
        taskRegistry[task.taskName] = task

        for (dependsOnTaskName in dependsOn) {
            registerDependency(task.taskName, dependsOnTaskName)
        }
    }

    fun registerTask(task: Task, dependsOn: TaskName) = registerTask(task = task, dependsOn = listOf(dependsOn))

    fun registerDependency(taskName: TaskName, dependsOn: TaskName) {
        dependencies[taskName] = dependencies.getOrDefault(taskName, emptySet()) + dependsOn
    }

    fun build(): TaskGraph = TaskGraph(nameToTask = taskRegistry.toMap(), dependencies = dependencies.toMap())
}
