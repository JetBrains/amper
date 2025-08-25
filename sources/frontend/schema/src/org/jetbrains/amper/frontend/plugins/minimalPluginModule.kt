/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.MissingPropertiesHandler
import org.jetbrains.amper.frontend.aomBuilder.createSchemaNode
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * Needed by the `preparePlugins` stage to read the plugin information from the local plugin module, before the full
 * schema for `module.yaml` is available. This is required because the full schema depends on the plugins schema.
 */
class MinimalPluginModule : SchemaNode() {
    var product by value<ModuleProduct>()

    var plugin by value(::PluginDeclarationSchema)
}

fun tryReadMinimalPluginModule(
    problemReporter: ProblemReporter,
    frontendPathResolver: FrontendPathResolver,
    moduleFilePath: VirtualFile,
) : MinimalPluginModule? {
    return with(BuildCtx(frontendPathResolver, problemReporter)) {
        val pluginModuleTree = readTree(
            file = moduleFilePath,
            type = types.getDeclaration<MinimalPluginModule>(),
            reportUnknowns = false,
        )
        val refiner = TreeRefiner()
        val noContextsTree = refiner.refineTree(pluginModuleTree, EmptyContexts)
        val missingPropertiesHandler = object : MissingPropertiesHandler {
            var somePropertiesMissing = false
            override fun onMissingRequiredPropertyValue(
                trace: Trace,
                keyName: String,
                keyTrace: Trace?,
            ) {
                somePropertiesMissing = true
                if (keyName == "id") {
                    problemReporter.reportBundleError(
                        trace.asBuildProblemSource(), "plugin.id.not.defined"
                    )
                }
                // Others are going to be reported elsewhere
            }
        }
        createSchemaNode<MinimalPluginModule>(noContextsTree, missingPropertiesHandler)
            .takeUnless { missingPropertiesHandler.somePropertiesMissing }
    }
}