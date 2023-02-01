/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.tasks.Task

class TaskGraphBuilder {
    // do not give access to the graph while it's being built
    private val taskRegistry = mutableMapOf<TaskName, Task>()
    private val dependencies = mutableMapOf<TaskName, Set<TaskName>>()

    fun registerTask(name: TaskName, task: Task) {
        if (taskRegistry.contains(name)) {
            error("Task '$name' already exists")
        }
        taskRegistry[name] = task
    }

    fun registerDependency(taskName: TaskName, dependsOn: TaskName) {
        dependencies[taskName] = dependencies.getOrDefault(taskName, emptySet()) + dependsOn
    }

    fun build(): TaskGraph = TaskGraph(tasks = taskRegistry.toMap(), dependencies = dependencies.toMap())
}
