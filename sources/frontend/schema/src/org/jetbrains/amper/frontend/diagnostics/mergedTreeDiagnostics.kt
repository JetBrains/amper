/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.TreeRefiner

/**
 * Diagnostic, that want to analyze merged tree, focusing on specific scalar 
 * values instead of the tree structure..
 * 
 * See: [MergedTreeDiagnostics]
 */
interface MergedTreeDiagnostic {
    val diagnosticId: BuildProblemId

    fun analyze(root: MergedTree, minimalModule: MinimalModule, problemReporter: ProblemReporter): Unit?
}

/**
 * Get all registered [MergedTreeDiagnostic]s.
 */
// suppressed the unused warning because it's necessary for the disabled diagnostic that we'll put back eventually
fun MergedTreeDiagnostics(@Suppress("unused") refiner: TreeRefiner) = listOf(
    AndroidTooOldVersionFactory,
    LibShouldHavePlatforms,
    ProductPlatformIsUnsupported,
    ProductPlatformsShouldNotBeEmpty,
    TemplateNameWithoutPostfix,
    AliasesDontUseUndeclaredPlatform,
    AliasesAreNotIntersectingWithNaturalHierarchy,
//    UselessSettingValue(refiner), // TODO fix it if we want to restore it: AMPER-4489, AMPER-4490
)
