/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.EmptyVersionCatalog
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.catalogs.builtInCatalog
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.plus
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.completeTree
import org.jetbrains.amper.frontend.tree.instance
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.getDeclaration
import org.jetbrains.amper.problems.reporting.ProblemReporter

/**
 * Reads the effective [VersionCatalog] for the given [templateFile].
 *
 * This is a combination of the project catalog and the built-in catalogs provided by the enabled toolchains.
 */
context(problemReporter: ProblemReporter)
@UsedInIdePlugin
fun AmperProjectContext.readEffectiveCatalogForTemplate(templateFile: VirtualFile): VersionCatalog =
    context(frontendPathResolver, SchemaTypingContext(), problemReporter) {
        val templateTree = readTree(
            file = templateFile,
            declaration = contextOf<SchemaTypingContext>().getDeclaration<Template>(),
        )
        val refiner = TreeRefiner()
        // We can cast here, since we are not merging templates for now.
        // NOTE: That will change when nested templates are allowed.
        val noContextsTemplate = refiner.refineTree(templateTree, EmptyContexts)
            .completeTree()?.instance<Template>()
        val builtinCatalog = noContextsTemplate?.settings?.builtInCatalog() ?: EmptyVersionCatalog
        builtinCatalog + projectVersionsCatalog
    }
