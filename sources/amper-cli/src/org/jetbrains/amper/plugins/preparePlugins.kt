/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import com.android.utils.associateNotNull
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.CliProblemReporter
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.plugins.tryReadMinimalPluginModule
import org.jetbrains.amper.frontend.project.pluginInternalSchemaDirectory
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import kotlin.io.path.div

suspend fun preparePlugins(
    context: CliContext,
) {
    if (context.projectContext.pluginDependencies.isEmpty())
        return  // Early bail

    spanBuilder("Prepare plugins").use {
        val schemaDir = context.projectContext.pluginInternalSchemaDirectory
        val reporter = CliProblemReporter
        val pluginInfos = context.projectContext.pluginDependencies.associateNotNull { dep ->
            val pluginModuleDir = (context.projectRoot.path / dep.path).normalize()
            val pluginModuleFile = context.projectContext.amperModuleFiles
                .find { it.parent.toNioPath() == pluginModuleDir }

            if (pluginModuleFile == null) {
                reporter.reportBundleError(dep.trace.asBuildProblemSource(), "plugin.dependency.not.found", dep.path)
                return@associateNotNull null
            }

            val pluginModule = spanBuilder("Read minimal plugin module").use {
                tryReadMinimalPluginModule(
                    problemReporter = reporter,
                    frontendPathResolver = context.projectContext.frontendPathResolver,
                    moduleFilePath = pluginModuleFile,
                )
            }

            if (pluginModule == null) {
                // Already reported
                return@associateNotNull null
            }

            if (pluginModule.product.type != ProductType.JVM_AMPER_PLUGIN) {
                reporter.reportBundleError(pluginModule.product.trace.asBuildProblemSource(),
                    "plugin.unexpected.product.type", ProductType.JVM_AMPER_PLUGIN.value, pluginModule.product.type)
                return@associateNotNull null
            }

            pluginModuleDir to pluginModule.plugin
        }

        if (pluginInfos.isEmpty()) {
            return@use  // Nothing to prepare after validation
        }

        spanBuilder("Generate local plugins schema")
            .use {
                doPreparePlugins(
                    userCacheRoot = context.userCacheRoot,
                    executeOnChangedInputs = ExecuteOnChangedInputs(context.buildOutputRoot),
                    frontendPathResolver = context.projectContext.frontendPathResolver,
                    schemaDir = schemaDir,
                    plugins = pluginInfos,
                )
            }
    }
}
