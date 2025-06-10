/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
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
    fun ProblemReporterContext.analyze(root: MergedTree, minimalModule: MinimalModule): Unit?
}

/**
 * Get all registered [MergedTreeDiagnostic]s.
 */
fun MergedTreeDiagnostics(refiner: TreeRefiner) = listOf(
    AndroidTooOldVersionFactory,
    LibShouldHavePlatforms,
    ProductPlatformIsUnsupported,
    ProductPlatformsShouldNotBeEmpty,
    TemplateNameWithoutPostfix,
    UnresolvedTemplate,
    AliasesDontUseUndeclaredPlatform,
    AliasesAreNotIntersectingWithNaturalHierarchy,
    UselessSettingValue(refiner),
)