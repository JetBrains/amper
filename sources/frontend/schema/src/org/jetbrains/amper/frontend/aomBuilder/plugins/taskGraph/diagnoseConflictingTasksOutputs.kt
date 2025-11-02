/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph

import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.messages.PsiBuildProblemSource
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.Path
import kotlin.collections.component1
import kotlin.collections.component2

context(reporter: ProblemReporter)
internal fun diagnoseConflictingTasksOutputs(
    tasks: List<TaskFromPluginDescription>,
) {
    val taskOutputs: List<Pair<TraceablePath, TaskFromPluginDescription>> = tasks.flatMap { task ->
        task.outputs.map { it.path to task }
    }
    taskOutputs.groupByRoots(
        pathSelector = { (path, _) -> path.value },
    ).forEach { (root: Path, taskOutputs) ->
        val tasksToOutputs = taskOutputs.groupBy { (_, task) -> task }
        if (tasksToOutputs.size > 1) {
            // conflicting outputs
            val source = MultipleLocationsBuildProblemSource(
                sources = tasksToOutputs.values.map { outputs ->
                    // We choose `first()` here because conflicting paths per single tasks are reported elsewhere
                    val (path: TraceablePath, _) = outputs.first()
                    path.asBuildProblemSource() as PsiBuildProblemSource
                },
                groupingMessage = SchemaBundle.message("plugin.tasks.output.produced.by.multiple.grouping")
            )
            val taskNames = tasksToOutputs.keys.joinToString {
                FrontendTaskGraphBundle.message("task.graph.node.task",
                    it.name, it.appliedTo.userReadableName, it.pluginId.value)
            }
            reporter.reportBundleError(
                source, "plugin.tasks.output.produced.by.multiple", root, taskNames,
            )
        }
    }
}
