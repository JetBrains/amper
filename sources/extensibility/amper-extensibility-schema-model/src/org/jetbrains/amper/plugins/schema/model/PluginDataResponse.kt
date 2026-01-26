/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.schema.model

import kotlinx.serialization.Serializable
import org.jetbrains.amper.serialization.paths.SerializablePath

/**
 * Special response data structure that is sent by the `amper-schema-processor` to CLI's `preparePlugins` routine
 * via STDOUT.
 */
@Serializable
data class PluginDataResponse(
    val results: List<PluginDataWithDiagnostics>,
) {
    @Serializable
    data class PluginDataWithDiagnostics(
        val sourcePath: SerializablePath,
        val declarations: PluginData.Declarations,
        val diagnostics: List<Diagnostic> = emptyList(),
    )

    @Serializable
    data class Diagnostic(
        val diagnosticId: String,
        val message: String,
        val kind: DiagnosticKind,
        val location: SourceLocation,
    )

    @Serializable
    enum class DiagnosticKind {
        ErrorGeneric,
        ErrorUnresolvedLikeConstruct,
        WarningRedundant,
    }
}