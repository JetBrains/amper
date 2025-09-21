/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import com.android.utils.associateNotNull
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.frontend.plugins.parsePluginManifestFromModuleFile
import org.jetbrains.amper.frontend.project.pluginInternalDataFile
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.AmperCliIncrementalCache

/**
 * Silently prepares plugins on the best effort basis.
 * All the validation must be done separately.
 */
suspend fun preparePlugins(
    context: CliContext,
) {
    spanBuilder("Prepare plugins").use {
        val projectContext = context.projectContext
        val seenPluginIds = hashSetOf<String>()
        val pluginInfos = projectContext.pluginModuleFiles.associateNotNull { pluginModuleFile ->
            val pluginManifest = spanBuilder("Read plugin manifest").use {
                parsePluginManifestFromModuleFile(
                    frontendPathResolver = projectContext.frontendPathResolver,
                    moduleFile = pluginModuleFile,
                )
            } ?: return@associateNotNull null
            if (!seenPluginIds.add(pluginManifest.id)) {
                // Skip the plugin with a duplicate id
                return@associateNotNull null
            }

            pluginModuleFile.parent.toNioPath() to pluginManifest
        }

        if (pluginInfos.isEmpty()) {
            return@use  // Nothing to prepare after validation
        }

        // Note: plugin may have duplicate ids at this point.
        //  We process everything on the best-effort basis to report as much as possible.
        spanBuilder("Generate local plugins schema")
            .use {
                doPreparePlugins(
                    projectRoot = context.projectRoot,
                    userCacheRoot = context.userCacheRoot,
                    incrementalCache = AmperCliIncrementalCache(context.buildOutputRoot),
                    frontendPathResolver = projectContext.frontendPathResolver,
                    schemaFile = projectContext.pluginInternalDataFile,
                    plugins = pluginInfos,
                )
            }
    }
}
