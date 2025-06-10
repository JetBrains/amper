/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.reading.readTree


context(ProblemReporterContext)
fun readProject(
    resolver: FrontendPathResolver,
    projectFile: VirtualFile
): Project? =
    with(BuildCtx(resolver, this@ProblemReporterContext)) {
        val projectTree = readTree(projectFile, projectAType) ?: return null
        val refiner = TreeRefiner()
        // We can cast here because there is only one project file, thus no need to merge.
        val noContextsTree = refiner.refineTree(projectTree as MergedTree, EmptyContexts)
        createSchemaNode<Project>(noContextsTree)
    }