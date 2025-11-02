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

context(reporter: ProblemReporter, _: TaskGraphBuildContext)
internal fun diagnoseOverlappingPaths(
    task: TaskFromPluginDescription,
) {
    fun reportOverlappingPaths(paths: List<TraceablePath>) {
        paths.groupByRoots(
            pathSelector = { it.value }
        ).forEach { (root, nested) ->
            if (nested.size > 1) {
                val source = MultipleLocationsBuildProblemSource(
                    sources = nested.map { traceablePath ->
                        traceablePath.asBuildProblemSource() as PsiBuildProblemSource
                    },
                    groupingMessage = SchemaBundle.message("plugin.tasks.paths.nested.reserved.grouping"),
                )
                reporter.reportBundleError(source, "plugin.tasks.paths.nested.reserved", root.replaceKnownSuperpaths())
            }
        }
    }
    reportOverlappingPaths(task.outputs.map { (path, _) -> path })
    reportOverlappingPaths(task.inputs.map { it.path })
}