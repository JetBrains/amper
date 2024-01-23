/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.aomBuilder.FioContext
import org.jetbrains.amper.frontend.aomBuilder.tryGetCatalogFor
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.convertTemplate
import java.nio.file.Path


context(ProblemReporterContext, FrontendPathResolver)
fun readTemplate(fioCtx: FioContext, path: Path): ModelInit.TemplateHolder? = with(ConvertCtx(path.parent, this@FrontendPathResolver)) {
    val nonProcessed = convertTemplate(path) ?: return@with null
    val chosenCatalog = tryGetCatalogFor(fioCtx, path, nonProcessed)
    val processed = nonProcessed.replaceCatalogDependencies(chosenCatalog)
    ModelInit.TemplateHolder(processed, chosenCatalog)
}

context(ProblemReporterContext, FrontendPathResolver)
fun Module.readTemplatesAndMerge(fioCtx: FioContext): Module {
    val readTemplates = apply?.mapNotNull { readTemplate(fioCtx, it)?.template } ?: emptyList()
    val toMerge = readTemplates + this

    val merged = toMerge.reduce { first, second -> first.merge(second, ::Template) }

    val result = Module()
    // Copy non-merge-able stuff.
    result.mergeNode(this@readTemplatesAndMerge, { result }) {
        mergeNodeProperty(Module::product) { it }
        mergeNodeProperty(Module::module) { it }
        mergeNodeProperty(Module::aliases) { it }
        mergeNodeProperty(Module::apply) { it }
    }

    // Copy all other fields.
    result.mergeNode(merged, { result }) {
        mergeNodeProperty(Base::repositories) { it }
        mergeNodeProperty(Base::settings) { it }
        mergeNodeProperty(Base::`test-settings`) { it }
        mergeNodeProperty(Base::dependencies) { it }
        mergeNodeProperty(Base::`test-dependencies`) { it }
    }

    return result
}