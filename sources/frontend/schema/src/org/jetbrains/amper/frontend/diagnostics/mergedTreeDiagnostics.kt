/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * Factory to provide diagnostics on a merged tree.
 *
 * Use this factory to focus on specific scalar values instead of the tree structure.
 *
 * @see [treeDiagnosticFactories]
 */
interface TreeDiagnosticFactory {
    fun analyze(root: TreeNode, minimalModule: MinimalModule, problemReporter: ProblemReporter): Unit?
}

/**
 * Get all registered [TreeDiagnosticFactory]s.
 */
// suppressed the unused warning because it's necessary for the disabled diagnostic that we'll put back eventually
fun treeDiagnosticFactories(@Suppress("unused") refiner: TreeRefiner) = listOf<TreeDiagnosticFactory>(
    AndroidTooOldVersionFactory,
    IncorrectSettingsSectionFactory,
    KotlinCompilerVersionDiagnosticsFactory,
    PluginInfoDescriptionIsDeprecatedFactory,
    TemplateNameWithoutPostfix,
    UnknownQualifiers,
    UnsupportedLayoutDiagnosticFactory,
    ValidXmlValidation,
    // TODO fix it if we want to restore it: AMPER-4489, AMPER-4490
    //  see the comment near the diagnostic class for some technical details
    //UselessSettingValue(refiner),
)
