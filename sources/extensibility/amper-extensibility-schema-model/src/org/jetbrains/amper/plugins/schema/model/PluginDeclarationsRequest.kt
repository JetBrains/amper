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
data class PluginDeclarationsRequest(
    val requests: List<Request>,
    val jdkPath: PathAsString,
    val librariesPaths: List<PathAsString>,
) {
    @Serializable
    data class Request(
        /**
         * Module name to use for internal processing.
         */
        val moduleName: String,

        /**
         * The directory with the sources to analyze.
         */
        val sourceDir: PathAsString,

        /**
         * The optional schema name for the plugin schema extension class for module.
         * Always `null` for libraries.
         */
        val moduleExtensionSchemaName: SchemaName? = null,
    )
}