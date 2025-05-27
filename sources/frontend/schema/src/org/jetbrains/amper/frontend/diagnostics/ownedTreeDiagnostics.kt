/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.tree.OwnedTree


/**
 * Diagnostic, that want to preserve module file tree structure while analyzing.
 * 
 * See: [OwnedTreeDiagnostics].
 */
interface OwnedTreeDiagnostic {
    val diagnosticId: BuildProblemId
    fun ProblemReporterContext.analyze(root: OwnedTree, minimalModule: MinimalModule): Unit?
}

/**
 * Get all registered [MergedTreeDiagnostic]s.
 */
val OwnedTreeDiagnostics = listOf(
    UnknownQualifiers,
    IncorrectSettingsLocation,
)