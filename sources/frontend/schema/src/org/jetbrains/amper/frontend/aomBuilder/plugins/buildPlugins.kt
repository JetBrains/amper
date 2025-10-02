/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.ModuleBuildCtx
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.plugins.PluginYamlRoot
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyLocal
import org.jetbrains.amper.frontend.plugins.generated.ShadowDependencyMaven
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import org.jetbrains.amper.stdlib.collections.distinctBy
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

internal fun BuildCtx.buildPlugins(
    pluginData: List<PluginData>,
    projectContext: AmperProjectContext,
    modules: List<ModuleBuildCtx>,
) {
    val seenPluginIds = hashMapOf<String, MutableList<TraceableString>>()
    val plugins = projectContext.pluginModuleFiles.mapNotNull mapPlugins@ { pluginModuleFile ->
        val pluginModule = modules.find { it.moduleFile == pluginModuleFile }
            ?: return@mapPlugins null

        run { // Report invalid product type
            val product = pluginModule.moduleCtxModule.product
            if (product.type != ProductType.JVM_AMPER_PLUGIN) {
                problemReporter.reportBundleError(
                    product.asBuildProblemSource(),
                    "plugin.unexpected.product.type", ProductType.JVM_AMPER_PLUGIN.value, product.type
                )
                return@mapPlugins null
            }
        }

        val pluginId = pluginModule.moduleCtxModule.plugin!!.id // safe - default is always set
        if (pluginId.value in seenPluginIds) {
            seenPluginIds[pluginId.value]!!.add(pluginId)
            return@mapPlugins null // Skip the duplicate
        } else {
            seenPluginIds[pluginId.value] = mutableListOf(pluginId)
        }

        val pluginData = pluginData.find { it.id.value == pluginId.value }
            ?: return@mapPlugins null

        pluginModule.moduleCtxModule.plugin?.schemaExtensionClassName?.let { moduleExtensionSchemaName ->
            if (pluginData.declarations.classes.none { it.name.qualifiedName == moduleExtensionSchemaName.value }) {
                problemReporter.reportBundleError(
                    source = moduleExtensionSchemaName.asBuildProblemSource(),
                    "plugin.missing.schema.class", moduleExtensionSchemaName,
                    problemType = BuildProblemType.UnresolvedReference,
                )
            }
        }

        val pluginFile = run { // Locate plugin.yaml
            val pluginModuleRoot = pluginModule.moduleFile.parent.toNioPath()
            val pluginFile = pluginModuleRoot / "plugin.yaml"
            if (!pluginFile.isRegularFile()) {
                // We assume for now that missing plugin.yaml is a valid scenario
                // TODO: Maybe at least report it?
                return@mapPlugins null
            }
            pluginFile
        }

        PluginTreeReader(
            projectContext = projectContext,
            pluginData = pluginData,
            pluginFile = projectContext.frontendPathResolver.loadVirtualFile(pluginFile),
            pluginModule = pluginModule.module,
            buildCtx = this@buildPlugins,
        )
    }

    // Report duplicate IDs
    for ((id, traceableIds) in seenPluginIds) {
        if (traceableIds.size < 2) continue
        val source = MultipleLocationsBuildProblemSource(
            sources = traceableIds.map { it.asBuildProblemSource() as FileBuildProblemSource },
            groupingMessage = SchemaBundle.message("plugin.id.duplicate.grouping", id)
        )
        problemReporter.reportBundleError(source, "plugin.id.duplicate")
    }

    for (moduleBuildCtx in modules) for (plugin in plugins) {
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
                inputs = pathsCollector.allInputPaths.toList(),
                requestedModuleSources = pathsCollector.moduleSourcesNodes.mapNotNull { (node, location) ->
                    val module = node.from.resolve(modules) ?: return@mapNotNull null
                    TaskFromPluginDescription.ModuleSourcesRequest(
                        node = node,
                        from = module,
                        propertyLocation = location,
                    )
                },
                requestedClasspaths = pathsCollector.classpathNodes.map { (node, propertyLocation) ->
                    val localModules = node.dependencies.filterIsInstance<ShadowDependencyLocal>()
                        .mapNotNull { it.resolve(modules) }
                    TaskFromPluginDescription.ClasspathRequest(
                        node = node,
                        localDependencies = localModules.distinct(),
                        // TODO: validate maven dependencies here?
                        //  blocker: maven coordinates diagnostics are part of the DR and are not accessible here
                        externalDependencies = node.dependencies.filterIsInstance<ShadowDependencyMaven>()
                            .map { it.coordinates },
                        propertyLocation = propertyLocation,
                    )
                },
                outputs = outputsToMarks,
                codeSource = plugin.pluginModule,
                explicitOptOutOfExecutionAvoidance = taskInfo.optOutOfExecutionAvoidance,
            )
        }
    }
}

context(buildContext: BuildCtx)
private fun ShadowDependencyLocal.resolve(
    modules: List<ModuleBuildCtx>,
): AmperModule? {
    val module = modules.find { it.module.source.moduleDir == modulePath }
    if (module == null) {
        buildContext.problemReporter.reportBundleError(
            // TODO: Relative path (as it was specified) would be better?
            //  blocker: that information is lost currently.
            asBuildProblemSource(), "unresolved.module", modulePath.pathString,
        )
        return null
    }
    return module.module
}
