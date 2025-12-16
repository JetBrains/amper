/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.problems.reporting.ProblemReporter

context(problemReporter: ProblemReporter)
internal fun readProject(
    resolver: FrontendPathResolver,
    projectFile: VirtualFile,
): Project = context(resolver, problemReporter, SchemaTypingContext()) {
    val projectTree = readTree(projectFile, contextOf<SchemaTypingContext>().getDeclaration<Project>())
    val noContextsTree = TreeRefiner().refineTree(projectTree, EmptyContexts)
    createSchemaNode<Project>(noContextsTree)
        ?: error("No required values must be in the project schema!")
}
