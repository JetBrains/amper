/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import kotlinx.coroutines.coroutineScope
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.CliProblemReporter
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.frontend.plugins.tryReadMinimalPluginModule
import org.jetbrains.amper.frontend.project.pluginInternalSchemaDirectory
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.telemetry.use
import kotlin.io.path.div
import kotlin.io.path.pathString

suspend fun preparePlugins(
    context: CliContext,
) = coroutineScope prepare@{
    if (context.projectContext.pluginDependencies.isEmpty())
        return@prepare  // Early bail

    spanBuilder("Prepare plugins").use {
        val schemaDir = context.projectContext.pluginInternalSchemaDirectory
        val pluginPreparer = PluginPreparer(
            scope = this,
            userCacheRoot = context.userCacheRoot,
            projectRoot = context.projectRoot,
            tempRoot = context.projectTempRoot,
            buildOutputRoot = context.buildOutputRoot,
            schemaDir = schemaDir,
        )

        // TODO: Maybe parallelize these, if possible
        context.projectContext.pluginDependencies.forEach { dep ->

            val pluginModuleDir = (context.projectRoot.path / dep.path).normalize()

            spanBuilder("Prepare local plugin")
                .setAttribute("module-dir", pluginModuleDir.pathString)
                .use {
                    val pluginModuleFile = context.projectContext.amperModuleFiles
                        .find { it.parent.toNioPath() == pluginModuleDir }
                        ?: userReadableError("Unable to resolve plugin ${dep.path}, ensure such module exists in the project")

                    val pluginModule = spanBuilder("Read minimal plugin module").use {
                        tryReadMinimalPluginModule(
                            problemReporter = CliProblemReporter,
                            frontendPathResolver = context.projectContext.frontendPathResolver,
                            moduleFilePath = pluginModuleFile,
                        )
                    }

                    if (pluginModule.product.type != ProductType.JVM_AMPER_PLUGIN) {
                        userReadableError(
                            "Unexpected product type for plugin. " +
                                    "Expected: ${ProductType.JVM_AMPER_PLUGIN.value}, got: ${pluginModule.product.type}"
                        )
                    }

                    spanBuilder("Generate local plugin schema")
                        .setAttribute("plugin-id", pluginModuleDir.pathString)
                        .use {
                            pluginPreparer.prepareLocalPlugin(
                                pluginModuleDir = pluginModuleDir,
                                pluginInfo = pluginModule.plugin,
                            )
                        }
                }
        }

        spanBuilder("Clean stale plugin files").use {
            pluginPreparer.cleanUnknownFiles()
        }
    }
}
