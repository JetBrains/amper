/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.pluginInternalDataFile
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.problems.reporting.GlobalBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.amper.problems.reporting.ProblemReporter
import kotlin.io.path.exists
import kotlin.io.path.readText

private val Format = Json {
    ignoreUnknownKeys = true
}

context(problemReporter: ProblemReporter)
fun AmperProjectContext.loadPreparedPluginData(): List<PluginData> {
    return if (pluginModuleFiles.isNotEmpty() && pluginInternalDataFile.exists()) {
        try {
            Format.decodeFromString<List<PluginData>>(pluginInternalDataFile.readText())
        } catch (e: SerializationException) {
            @OptIn(NonIdealDiagnostic::class)
            problemReporter.reportBundleError(
                GlobalBuildProblemSource,
                "plugin.unsupported.plugin.data",
                e.message,
                level = Level.Warning,
            )
            emptyList()
        }
    } else emptyList()
}