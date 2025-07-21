/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.readModuleMergedTree
import org.jetbrains.amper.frontend.catalogs.GradleVersionsCatalogFinder
import org.jetbrains.amper.frontend.catalogs.IncorrectCatalogDetection
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.TreeRefiner

data class MergedTreeHolder(
    val mergedTree: MergedTree,
    val refiner: TreeRefiner,
)

/**
 * Reads the merged tree of an Amper module file and returns it with the corresponding [TreeRefiner].
 */
@IncorrectCatalogDetection
@UsedInIdePlugin
fun readAmperModuleTree(moduleFile: PsiFile, problemReporter: ProblemReporter): MergedTreeHolder? {
    val pathResolver = FrontendPathResolver(moduleFile.project)
    val projectVersionsCatalog = with(pathResolver) {
        GradleVersionsCatalogFinder.findGradleVersionCatalogUpTheTreeFrom(moduleFile.virtualFile)
    }
    val buildCtx = BuildCtx(pathResolver = pathResolver, problemReporter = problemReporter)
    return with(problemReporter) {
        val (_, tree, refiner) = buildCtx.readModuleMergedTree(moduleFile.virtualFile, projectVersionsCatalog)
            ?: return null
        MergedTreeHolder(tree, refiner)
    }
}
