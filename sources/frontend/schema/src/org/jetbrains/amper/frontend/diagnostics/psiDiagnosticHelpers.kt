/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.readModuleMergedTree
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.tree.Merged
import org.jetbrains.amper.frontend.tree.TreeRefiner

@UsedInIdePlugin
data class MergedTreeHolder(
    val mergedTree: Merged,
    val refiner: TreeRefiner,
)

/**
 * Reads the merged tree of an Amper module file and returns it with the corresponding [TreeRefiner].
 */
context(problemReporter: ProblemReporter)
@UsedInIdePlugin
fun AmperProjectContext.readAmperModuleTree(moduleFile: VirtualFile): MergedTreeHolder? {
    val buildCtx = BuildCtx(pathResolver = frontendPathResolver, problemReporter = problemReporter)
    val moduleBuildCtx = buildCtx.readModuleMergedTree(moduleFile, projectVersionsCatalog) ?: return null
    return MergedTreeHolder(moduleBuildCtx.mergedTree, moduleBuildCtx.refiner)
}
