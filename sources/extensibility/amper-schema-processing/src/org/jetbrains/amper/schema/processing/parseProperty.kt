/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.psi
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor

context(session: KaSession, _: DiagnosticsReporter, _: SymbolsCollector)
internal fun parseProperty(
    property: KtProperty,
): PluginData.ClassData.Property? {
    val name = property.name ?: return null // invalid Kotlin
    val nameIdentifier = property.nameIdentifier ?: return null // invalid Kotlin

    property.overrideModifier()?.let {
        reportError(it, "schema.forbidden.property.override")
    }
    property.extensionReceiver()?.let {
        reportError(it, "schema.forbidden.property.extension")
    }
    if (property.isVar) {
        reportError(property.valOrVarKeyword, "schema.forbidden.property.mutable")
    }

    val type = with(session) {
        property.returnType
    }.parseSchemaType(origin = { property.typeReference ?: property })

    val default = with(session) { property.symbol as KaPropertySymbol }.getter?.let { getter ->
        getter.psiSafe<KtPropertyAccessor>()?.let { psi ->
            if (psi.bodyBlockExpression != null) {
                reportError(getter.psi(), "schema.defaults.invalid.getter.block"); null
            } else if (type != null) psi.bodyExpression?.let { expression ->
                parseDefaultExpression(expression, type)
            } else null
        }
    }

    if (type == null) return null

    return PluginData.ClassData.Property(
        name = name,
        type = type,
        default = default,
        doc = property.getDefaultDocString(),
        origin = nameIdentifier.getSourceLocation(),
    )
}
