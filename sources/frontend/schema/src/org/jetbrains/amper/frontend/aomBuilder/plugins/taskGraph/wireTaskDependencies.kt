/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph

/**
 * Fill the [org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription.dependsOn] property
 * according to the edges in the [taskGraph].
 */
internal fun wireTaskDependencies(
    taskGraph: TaskGraph,
) {
    taskGraph.nodes.filterIsInstance<TaskGraph.Node.PluginTask>().forEach { taskNode ->
        taskNode.description.dependsOn = taskGraph[taskNode].mapNotNull {
            (it.target as? TaskGraph.Node.PluginTask)?.description
        }
    }
}