/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.ModuleBuildCtx
import org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph.TaskGraphBuildContext
import org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph.buildTaskGraph
import org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph.diagnoseConflictingTasksOutputs
import org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph.diagnoseOverlappingPaths
import org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph.diagnoseTaskDependencyLoops
import org.jetbrains.amper.frontend.aomBuilder.plugins.taskGraph.wireTaskDependencies
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * Read all `plugin.yaml` for plugins in the project. Apply parsed plugins to every module that has them enabled.
 */
context(problemReporter: ProblemReporter, _: SchemaTypingContext, _: FrontendPathResolver)
internal fun buildPlugins(
    pluginData: List<PluginData>,
    projectContext: AmperProjectContext,
    modules: List<ModuleBuildCtx>,
) {
    val pluginReaders = createPluginReaders(projectContext, modules, pluginData)

    for (moduleBuildCtx in modules) {
        applyPlugins(pluginReaders, moduleBuildCtx, modules)
    }

    val allTasksDescriptions: List<TaskFromPluginDescription> = modules.flatMap { it.module.tasksFromPlugins }
    val context = TaskGraphBuildContext(
        buildDir = projectContext.projectBuildDir,
        projectRootDir = projectContext.projectRootDir.toNioPath(),
    )
    context(context) {
        for (task in allTasksDescriptions) {
            diagnoseOverlappingPaths(task)
        }

        diagnoseConflictingTasksOutputs(allTasksDescriptions)

        val taskGraph = buildTaskGraph(
            tasks = allTasksDescriptions,
            modules = modules.map { it.module },
        )

        diagnoseTaskDependencyLoops(taskGraph)
        wireTaskDependencies(taskGraph)
    }
}
