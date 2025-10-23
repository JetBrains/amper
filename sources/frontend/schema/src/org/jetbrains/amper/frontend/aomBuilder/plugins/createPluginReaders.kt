/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.ModuleBuildCtx
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.FileBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.MultipleLocationsBuildProblemSource
import kotlin.io.path.div
import kotlin.io.path.isRegularFile

context(buildContext: BuildCtx)
internal fun createPluginReaders(
    projectContext: AmperProjectContext,
    modules: List<ModuleBuildCtx>,
    pluginData: List<PluginData>,
): List<PluginTreeReader> {
    val seenPluginIds = hashMapOf<String, MutableList<TraceableString>>()
    val pluginReaders = projectContext.pluginsModuleFiles.mapNotNull mapPlugins@{ pluginModuleFile ->
        val pluginModule = modules.find { it.moduleFile == pluginModuleFile }
            ?: return@mapPlugins null

        run { // Report invalid product type
            val product = pluginModule.moduleCtxModule.product
            if (product.type != ProductType.JVM_AMPER_PLUGIN) {
                buildContext.problemReporter.reportBundleError(
                    product.asBuildProblemSource(),
                    "plugin.unexpected.product.type", ProductType.JVM_AMPER_PLUGIN.value, product.type
                )
                return@mapPlugins null
            }
        }

        val pluginId = pluginModule.moduleCtxModule.pluginInfo!!.id // safe - default is always set
        if (pluginId.value in seenPluginIds) {
            seenPluginIds[pluginId.value]!!.add(pluginId)
            return@mapPlugins null // Skip the duplicate
        } else {
            seenPluginIds[pluginId.value] = mutableListOf(pluginId)
        }

        val pluginData = pluginData.find { it.id.value == pluginId.value }
            ?: return@mapPlugins null

        pluginModule.moduleCtxModule.pluginInfo?.settingsClass?.let { settingsClass ->
            if (pluginData.declarations.classes.none { it.name.qualifiedName == settingsClass.value }) {
                buildContext.problemReporter.reportBundleError(
                    source = settingsClass.asBuildProblemSource(),
                    "plugin.missing.schema.class", settingsClass,
                    problemType = BuildProblemType.UnresolvedReference,
                )
            }
        }

        val pluginFile = run { // Locate plugin.yaml
            val pluginModuleRoot = pluginModule.moduleFile.parent
            val pluginFile = pluginModuleRoot.toNioPath() / "plugin.yaml"
            if (!pluginFile.isRegularFile()) {
                buildContext.problemReporter.reportBundleError(
                    source = buildContext.pathResolver.toPsiDirectory(pluginModuleRoot)!!.asBuildProblemSource(),
                    messageKey = "plugin.missing.plugin.yaml",
                    level = Level.Warning,
                )
                return@mapPlugins null
            }
            pluginFile
        }

        PluginTreeReader(
            projectContext = projectContext,
            pluginData = pluginData,
            pluginFile = projectContext.frontendPathResolver.loadVirtualFile(pluginFile),
            pluginModule = pluginModule.module,
            buildCtx = buildContext,
        )
    }

    for ((id, traceableIds) in seenPluginIds) {
        if (traceableIds.size < 2) continue
        val source = MultipleLocationsBuildProblemSource(
            sources = traceableIds.map { it.asBuildProblemSource() as FileBuildProblemSource },
            groupingMessage = SchemaBundle.message("plugin.id.duplicate.grouping", id)
        )
        buildContext.problemReporter.reportBundleError(source, "plugin.id.duplicate")
    }

    return pluginReaders
}