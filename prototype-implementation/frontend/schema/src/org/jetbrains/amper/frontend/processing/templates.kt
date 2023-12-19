/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.ReaderCtx
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Template
import org.jetbrains.amper.frontend.schemaConverter.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.convertTemplateViaSnake
import java.nio.file.Path

context(ProblemReporterContext, ReaderCtx)
fun Module.readTemplatesAndMerge(): Module {
    fun readTemplate(path: Path): Template? = path2Reader(path)?.let {
        with(ConvertCtx(path.parent)) { convertTemplate { it } }
    }
    val readTemplates = apply.value?.mapNotNull { readTemplate(it) } ?: emptyList()
    val toMerge = readTemplates + this

    // We are sure that last instance is a Module and not null, so we can cast.
    val merged = toMerge.reduce { first, second -> first.merge(second, ::Template) }

    return Module().apply {
        // Copy non-merge-able stuff.
        product(this@readTemplatesAndMerge.product.value)
        aliases(this@readTemplatesAndMerge.aliases.value)
        apply(this@readTemplatesAndMerge.apply.value)

        // Copy all other fields.
        repositories(merged.repositories.value)
        dependencies(merged.dependencies.value)
        settings(merged.settings.value)
        `test-dependencies`(merged.`test-dependencies`.value)
        `test-settings`(merged.`test-settings`.value)
    }
}