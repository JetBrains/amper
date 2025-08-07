/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.psi
import org.jetbrains.kotlin.psi.KtProperty

context(session: KaSession, _: ErrorReporter, _: SymbolsCollector)
internal fun parseProperty(
    property: KtProperty,
): PluginData.ClassData.Property? {
    val name = property.name ?: return null // invalid Kotlin

    property.overrideModifier()?.let {
        reportError(it, "schema.forbidden.property.override")
    }
    property.extensionReceiver()?.let {
        reportError(it, "schema.forbidden.property.extension")
    }
    if (property.isVar) {
        reportError(property.valOrVarKeyword, "schema.forbidden.property.mutable")
    }
    with(session) { property.symbol as KaPropertySymbol }.getter?.let { getter ->
        if (getter.hasBody) {
            reportError(getter.psi(), "schema.forbidden.property.defaults")
            // TODO: Implement defaults parsing here in the future
        }
    }

    return with(session) {
        property.returnType
    }.parseSchemaType(origin = { property.typeReference ?: property })?.let {
        PluginData.ClassData.Property(
            name = name,
            type = it,
            doc = property.getDefaultDocString(),
        )
    }
}