/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import kotlinx.coroutines.coroutineScope
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.CliProblemReporter
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.frontend.plugins.tryReadMinimalPluginModule
import org.jetbrains.amper.frontend.project.pluginInternalSchemaDirectory
import org.jetbrains.amper.frontend.project.projectLocalCacheDirectory
import org.jetbrains.amper.frontend.schema.ProductType
import kotlin.io.path.div

suspend fun preparePlugins(
    context: CliContext,
) = coroutineScope prepare@{
    if (context.projectContext.pluginDependencies.isEmpty())
        return@prepare  // Early bail

    val schemaDir = context.projectContext.pluginInternalSchemaDirectory
    val pluginPreparer = PluginPreparer(
        scope = this,
        userCacheRoot = context.userCacheRoot,
        projectRoot = context.projectRoot,
        tempRoot = context.projectTempRoot,
        projectLocalCacheDir = context.projectContext.projectLocalCacheDirectory,
        schemaDir = schemaDir,
    )

    // TODO: Maybe parallelize these, if possible
    context.projectContext.pluginDependencies.forEach { dep ->
        // FIXME: report dep.path!!
        val pluginModuleDir = (context.projectRoot.path / dep.path!!).normalize()

        val pluginModuleFile = context.projectContext.amperModuleFiles
            .find { it.parent.toNioPath() == pluginModuleDir }
            ?: userReadableError("Unable to resolve plugin ${dep.path}, ensure such module exists in the project")

        val pluginModule = tryReadMinimalPluginModule(
            problemReporter = CliProblemReporter,
            frontendPathResolver = context.projectContext.frontendPathResolver,
            moduleFilePath = pluginModuleFile,
        ) ?: userReadableError("Unable to parse ${pluginModuleFile.path} for the plugin")

        if (pluginModule.product.type != ProductType.JVM_AMPER_PLUGIN) {
            userReadableError(
                "Unexpected product type for plugin. " +
                        "Expected: ${ProductType.JVM_AMPER_PLUGIN.value}, got: ${pluginModule.product.type}"
            )
        }

        pluginPreparer.prepareLocalPlugin(
            pluginModuleDir = pluginModuleDir,
            pluginInfo = pluginModule.plugin,
        )
    }

    pluginPreparer.cleanUnknownFiles()
}