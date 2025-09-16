/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.schema.model

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.TestOnly
import java.io.File
import kotlin.io.path.Path

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
        val moduleExtensionSchemaName: String? = null,

        /**
         * Whether to allow internal/unreleased features (builtin API mode)
         */
        val isParsingAmperApi: Boolean = false,
    )
}

/**
 * Creates a [PluginDeclarationsRequest] inferring most of the required dependencies from the calling environment.
 */
@TestOnly
fun PluginDeclarationsRequest(
    requests: List<PluginDeclarationsRequest.Request>,
    includeFoundExtensibilityApi: Boolean = true,
) : PluginDeclarationsRequest {
    val classpath = System.getProperty("java.class.path")!!.split(File.pathSeparator)
    return PluginDeclarationsRequest(
        requests = requests,
        jdkPath = Path(System.getProperty("java.home")!!),
        librariesPaths = buildList {
            add(Path(classpath.first { "kotlin-stdlib-" in it }))
            if (includeFoundExtensibilityApi) {
                add(Path(classpath.first { "amper-extensibility-api" in it }))
            }
        }
    )
}