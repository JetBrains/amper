/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.catalogs.builtInCatalog
import org.jetbrains.amper.frontend.catalogs.plus
import org.jetbrains.amper.frontend.catalogs.substituteCatalogDependencies
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.reading.readTree

/**
 * Reads the effective [VersionCatalog] for the given [templateFile].
 * This is a combination of the project catalog and the built-in catalogs provided by the enabled toolchains.
 */
context(problemReporter: ProblemReporter)
@UsedInIdePlugin
fun AmperProjectContext.readEffectiveCatalogForTemplate(templateFile: VirtualFile): VersionCatalog? =
    with(frontendPathResolver) {
        readTemplate(templateFile = templateFile, projectVersionCatalog = projectVersionsCatalog)?.chosenCatalog
    }

context(problemReporter: ProblemReporter, frontendPathResolver: FrontendPathResolver)
internal fun readTemplate(
    templateFile: VirtualFile,
    projectVersionCatalog: VersionCatalog?,
): ModelInit.TemplateHolder? = with(BuildCtx(pathResolver = frontendPathResolver, problemReporter = problemReporter)) {
    val templateTree = readTree(templateFile, templateAType) ?: return null
    val mergedTemplateTree = treeMerger.mergeTrees(templateTree)
    val refiner = TreeRefiner()
    // We can cast here, since we are not merging templates for now.
    // NOTE: That will change when nested templated will be allowed.
    val noContextsTree = refiner.refineTree(mergedTemplateTree, EmptyContexts)
    val noContextsTemplate = createSchemaNode<Template>(noContextsTree)
    val catalog = projectVersionCatalog + noContextsTemplate.settings.builtInCatalog()
    val substituted = mergedTemplateTree.substituteCatalogDependencies(catalog)
    val substitutedRefined = refiner.refineTree(substituted, EmptyContexts)
    val readTemplate = createSchemaNode<Template>(substitutedRefined)
    ModelInit.TemplateHolder(readTemplate, catalog)
}
