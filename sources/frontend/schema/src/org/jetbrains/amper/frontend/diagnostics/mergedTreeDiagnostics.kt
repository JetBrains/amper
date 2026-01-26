/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * Diagnostic, that want to analyze merged tree, focusing on specific scalar 
 * values instead of the tree structure..
 * 
 * See: [treeDiagnostics]
 */
interface TreeDiagnostic {
    val diagnosticId: BuildProblemId

    fun analyze(root: TreeNode, minimalModule: MinimalModule, problemReporter: ProblemReporter): Unit?
}

/**
 * Get all registered [TreeDiagnostic]s.
 */
// suppressed the unused warning because it's necessary for the disabled diagnostic that we'll put back eventually
fun treeDiagnostics(@Suppress("unused") refiner: TreeRefiner) = listOf(
    UnknownQualifiers,
    IncorrectSettingsLocation,
    AndroidTooOldVersionFactory,
    TemplateNameWithoutPostfix,
    KotlinCompilerVersionDiagnosticsFactory,
    UnsupportedLayoutDiagnosticFactory,
    // TODO fix it if we want to restore it: AMPER-4489, AMPER-4490
    //  see the comment near the diagnostic class for some technical details
    //UselessSettingValue(refiner),
)
