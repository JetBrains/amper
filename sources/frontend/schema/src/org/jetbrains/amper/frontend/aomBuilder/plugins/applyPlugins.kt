/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.aomBuilder.ModuleBuildCtx
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyLocal
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyMaven
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.stdlib.collections.distinctBy
import kotlin.collections.iterator
import kotlin.collections.plus
import kotlin.io.path.pathString

/**
 * Apply all enabled [plugins] to [moduleBuildCtx].
 * [allModules] is for the reference.
 */
context(problemReporter: ProblemReporter)
internal fun applyPlugins(
    plugins: List<PluginTreeReader>,
    moduleBuildCtx: ModuleBuildCtx,
    allModules: List<ModuleBuildCtx>,
) {
    for (plugin in plugins) {
        val appliedPlugin: PluginYamlRoot = plugin.asAppliedTo(
            module = moduleBuildCtx,
        ) ?: continue
        for ((name, task) in appliedPlugin.tasks) {
            val taskInfo = task.action.taskInfo
            val pathsCollector = InputOutputCollector()
            pathsCollector.gatherPaths(task.action)
            val outputMarks = task.markOutputsAs.distinctBy(
                selector = { it.path },
                onDuplicates = { path, duplicateMarks ->
                    val source = MultipleLocationsBuildProblemSource(
                        sources = duplicateMarks.mapNotNull { it.asBuildProblemSource() as? FileBuildProblemSource },
                        groupingMessage = SchemaBundle.message("plugin.invalid.mark.output.as.duplicates.grouping"),
                    )
                    problemReporter.reportBundleError(source, "plugin.invalid.mark.output.as.duplicates", path)
                }
            ).associateBy { it.path }
            outputMarks.forEach { (path, mark) ->
                if (path !in pathsCollector.allOutputPaths) {
                    problemReporter.reportBundleError(
                        mark.asBuildProblemSource(), "plugin.invalid.mark.output.as.no.such.path", path
                    )
                }
            }
            val outputsToMarks = pathsCollector.allOutputPaths.associateWith { path ->
                val mark = outputMarks[path] ?: return@associateWith null
                TaskFromPluginDescription.OutputMark(
                    kind = mark.kind,
                    associateWith = moduleBuildCtx.module.fragments.first {
                        it.isTest == mark.fragment.isTest && it.modifier == mark.fragment.modifier
                    }
                )
            }
            moduleBuildCtx.module.tasksFromPlugins += TaskFromPluginDescription(
                name = plugin.taskNameFor(moduleBuildCtx.module, name),
                actionFunctionJvmName = taskInfo.jvmFunctionName,
                actionClassJvmName = taskInfo.jvmFunctionClassName,
                actionArguments = task.action.valueHolders.mapValues { (_, v) -> v.value },
                explicitDependsOn = task.dependsOnSideEffectsOf,
                inputs = pathsCollector.allInputPaths.map { (path, inferTaskDependency) ->
                    TaskFromPluginDescription.InputPath(path, inferTaskDependency)
                },
                requestedModuleSources = pathsCollector.moduleSourcesNodes.mapNotNull { (node, location) ->
                    val module = node.from.resolve(allModules) ?: return@mapNotNull null
                    TaskFromPluginDescription.ModuleSourcesRequest(
                        node = node,
                        from = module,
                        propertyLocation = location,
                    )
                },
                requestedClasspaths = pathsCollector.classpathNodes.map { (node, propertyLocation) ->
                    val localModules = node.dependencies.filterIsInstance<ShadowDependencyLocal>()
                        .mapNotNull { it.resolve(allModules) }
                    TaskFromPluginDescription.ClasspathRequest(
                        node = node,
                        localDependencies = localModules.distinct(),
                        externalDependencies = node.dependencies.filterIsInstance<ShadowDependencyMaven>()
                            .map { it.coordinates },
                        propertyLocation = propertyLocation,
                    )
                },
                requestedCompilationArtifacts = pathsCollector.compilationArtifactNodes.mapNotNull { node ->
                    val from = node.from.resolve(allModules) ?: return@mapNotNull null
                    TaskFromPluginDescription.CompilationResultRequest(
                        node = node,
                        from = from,
                    )
                },
                outputs = outputsToMarks,
                codeSource = plugin.pluginModule,
                explicitOptOutOfExecutionAvoidance = taskInfo.optOutOfExecutionAvoidance,
            )
        }
    }
}

context(problemReporter: ProblemReporter)
private fun ShadowDependencyLocal.resolve(
    modules: List<ModuleBuildCtx>,
): AmperModule? {
    val module = modules.find { it.module.source.moduleDir == modulePath }
    if (module == null) {
        problemReporter.reportBundleError(
            // TODO: Relative path (as it was specified) would be better?
            //  blocker: that information is lost currently.
            asBuildProblemSource(), "unresolved.module", modulePath.pathString,
        )
        return null
    }
    return module.module
}