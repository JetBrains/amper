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
import org.jetbrains.amper.frontend.api.UnstableSchemaApi
import org.jetbrains.amper.frontend.api.toStringRepresentation
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.problems.reporting.NoopProblemReporter

/**
 * Needed by the `preparePlugins` stage to read the plugin information from the local plugin module, before the full
 * schema for `module.yaml` is available. This is required because the full schema depends on the plugins schema.
 */
class MinimalPluginModule : SchemaNode() {
    var product by value<ModuleProduct>()

    val plugin: PluginDeclarationSchema by nested()
}

interface PluginManifest {
    val id: String
    val description: String?
    val schemaExtensionClassName: String?
}

/**
 * Parses [PluginManifest] from the `module.yaml` file that is allegedly a plugin.
 * Does no error reporting, and the result is obtained on the best effort basis.
 *
 * @return plugin manifest on the best effort basis or `null` is this `module.yaml` file is not a plugin for sure.
 */
fun parsePluginManifestFromModuleFile(
    frontendPathResolver: FrontendPathResolver,
    moduleFile: VirtualFile,
) : PluginManifest? {
    with(BuildCtx(frontendPathResolver, NoopProblemReporter)) {
        val pluginModuleTree = readTree(
            file = moduleFile,
            type = types.getDeclaration<MinimalPluginModule>(),
            reportUnknowns = false,
            parseContexts = false,
        )
        val noContextsTree = TreeRefiner().refineTree(pluginModuleTree, EmptyContexts)
        val handler = object : MissingPropertiesHandler {
            var somePropertiesMissing = false
            override fun onMissingRequiredPropertyValue(trace: Trace, keyName: String, keyTrace: Trace?) {
                somePropertiesMissing = true
            }
        }
        val moduleHeader = createSchemaNode<MinimalPluginModule>(noContextsTree, handler)

        if (handler.somePropertiesMissing)
            return null

        if (moduleHeader.product.type != ProductType.JVM_AMPER_PLUGIN)
            return null

        return object : PluginManifest {
            override val id: String = moduleHeader.plugin.id?.value ?: moduleFile.parent.name
            override val description: String? = moduleHeader.plugin.description
            override val schemaExtensionClassName: String? = moduleHeader.plugin.schemaExtensionClassName?.value

            @OptIn(UnstableSchemaApi::class)
            override fun toString(): String {
                return "{schema='${moduleHeader.plugin.toStringRepresentation()}', moduleFile='${moduleFile.path}'}"
            }
        }
    }
}