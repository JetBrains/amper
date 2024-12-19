/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.aomBuilder.tryGetCatalogFor
import org.jetbrains.amper.frontend.api.copy
import org.jetbrains.amper.frontend.catalogs.VersionsCatalogProvider
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.psi.ConverterImpl


context(ProblemReporterContext)
internal fun readTemplate(catalogFinder: VersionsCatalogProvider, file: VirtualFile): ModelInit.TemplateHolder? {
    val converter =
        ConverterImpl(file.parent, catalogFinder.frontendPathResolver, this@ProblemReporterContext.problemReporter)
    val nonProcessed = converter.convertTemplate(file) ?: return null
    val chosenCatalog = with(converter) { catalogFinder.tryGetCatalogFor(file, nonProcessed) }
    val processed = nonProcessed.replaceCatalogDependencies(chosenCatalog)
    return ModelInit.TemplateHolder(processed, chosenCatalog)
}

context(ProblemReporterContext)
internal fun Module.readTemplatesAndMerge(catalogFinder: AmperProjectContext): Module {
    val readTemplates: List<Base> = apply
        ?.mapNotNull { catalogFinder.frontendPathResolver.loadVirtualFileOrNull(it.value) }
        ?.mapNotNull { readTemplate(catalogFinder, it)?.template } ?: emptyList()

    if (readTemplates.isEmpty()) return this
    val mergedTemplates: Base = readTemplates.reduce { base, overwrite ->
        base.mergeTemplate(overwrite, ::Template)
    }

    // Copy non-merge-able stuff.
    val result = Module().also {
        Module::product.copy(from = this, to = it)
        Module::module.copy(from = this, to = it)
        Module::aliases.copy(from = this, to = it)
        Module::apply.copy(from = this, to = it)
    }

    // Merge all other fields.
    mergedTemplates.mergeTemplate(this) { result }

    return result
}