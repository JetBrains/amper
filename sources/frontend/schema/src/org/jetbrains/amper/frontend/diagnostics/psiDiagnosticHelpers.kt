/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.aomBuilder.readModuleMergedTree
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.system.info.SystemInfo

@UsedInIdePlugin
data class MergedTreeHolder(
    val mergedTree: MappingNode,
    val refiner: TreeRefiner,
)

/**
 * Reads the merged tree of an Amper module file and returns it with the corresponding [TreeRefiner].
 */
context(problemReporter: ProblemReporter)
@UsedInIdePlugin
fun AmperProjectContext.readAmperModuleTree(moduleFile: VirtualFile): MergedTreeHolder? {
    val moduleBuildCtx = context(frontendPathResolver, SchemaTypingContext(), SystemInfo.CurrentHost) {
        readModuleMergedTree(moduleFile, projectVersionsCatalog) ?: return null
    }
    return MergedTreeHolder(moduleBuildCtx.mergedTree, moduleBuildCtx.refiner)
}
