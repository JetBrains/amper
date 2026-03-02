/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder.plugins

import org.jetbrains.amper.problems.reporting.DiagnosticId

/**
 * Diagnostics reported about plugin-related functionality.
 */
enum class PluginDiagnosticId : DiagnosticId {
    ConflictingMarkedPluginPaths,
    ConflictingPluginTaskPaths,
    PluginDescriptionShouldBeTopLevel,
    PluginDoesntRegisterAnyTasks,
    PluginDuplicateId,
    PluginMissingSchemaClass,
    PluginNotEnabledButConfigured,
    PluginYamlMissing,
    TaskDependencyLoop,
    TaskOutputProducedByMultipleTasks,
    UndeclaredMarkedOutputPath,
    UnexpectedPluginProductType,
    UnproducedInputInBuildDirectory,
}