/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.schema.model

import kotlinx.serialization.Serializable
import org.jetbrains.amper.plugins.schema.model.PluginData.SchemaName

/**
 * Special request data structure that is sent by the CLI's `preparePlugins` routine to the `amper-schema-processor`
 * via STDIN.
 */
@Serializable
data class PluginDataRequest(
    val plugins: List<PluginHeader>,
    val jdkPath: PathAsString,
    val librariesPaths: List<PathAsString>,
) {
    @Serializable
    data class PluginHeader(
        val pluginId: PluginData.Id,
        val sourceDir: PathAsString,
        val moduleExtensionSchemaName: SchemaName? = null,
        val description: String? = null,
    )
}