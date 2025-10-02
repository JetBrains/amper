/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.ModuleBuildCtx
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.plugins.schema.model.PluginData

/**
 * Read all `plugin.yaml` for plugins in the project. Apply parsed plugins to every module that has them enabled.
 */
internal fun BuildCtx.buildPlugins(
    pluginData: List<PluginData>,
    projectContext: AmperProjectContext,
    modules: List<ModuleBuildCtx>,
) {
    val pluginReaders = createPluginReaders(projectContext, modules, pluginData)

    for (moduleBuildCtx in modules) context(problemReporter) {
        applyPlugins(pluginReaders, moduleBuildCtx, modules)
    }
}
