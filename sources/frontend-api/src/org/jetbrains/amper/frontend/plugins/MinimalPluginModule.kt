/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.plugins

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.StringSemantics
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.types.SchemaType

/**
 * Needed by the `preparePlugins` stage to read the plugin information from the local plugin module, before the full
 * schema for `module.yaml` is available. This is required because the full schema depends on the plugins schema.
 */
class MinimalPluginModule : SchemaNode() {
    val product by value<ModuleProduct>()

    val pluginInfo: MinimalPluginDeclarationSchema by nested()
}

class MinimalPluginDeclarationSchema : SchemaNode() {
    val id by nullableValue<TraceableString>()
    val description by nullableValue<String>()
    @StringSemantics(SchemaType.StringType.Semantics.PluginSettingsClass)
    val settingsClass by nullableValue<TraceableString>()
}