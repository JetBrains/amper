/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.catalogs.VersionsCatalogProvider
import org.jetbrains.amper.frontend.catalogs.substituteCatalogDependencies
import org.jetbrains.amper.frontend.catalogs.tryGetCatalogFor
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.reading.readTree


context(ProblemReporterContext)
fun readTemplate(
    catalogFinder: VersionsCatalogProvider,
    tPath: VirtualFile,
): ModelInit.TemplateHolder? = with(BuildCtx(catalogFinder)) {
    val templateTree = readTree(tPath, templateAType) ?: return null
    val refiner = TreeRefiner()
    // We can cast here, since we are not merging templates for now.
    // NOTE: That will change when nested templated will be allowed.
    val noContextsTree = refiner.refineTree(templateTree as MergedTree, EmptyContexts)
    val noContextsTemplate = createSchemaNode<Template>(noContextsTree)
    val catalog = tryGetCatalogFor(tPath, noContextsTemplate.settings)
    // TODO Check if return null is good here.
    val substituted = templateTree.substituteCatalogDependencies(catalog) ?: return null
    val substitutedRefined = refiner.refineTree(substituted, EmptyContexts)
    val readTemplate = createSchemaNode<Template>(substitutedRefined)
    ModelInit.TemplateHolder(readTemplate, catalog)
}