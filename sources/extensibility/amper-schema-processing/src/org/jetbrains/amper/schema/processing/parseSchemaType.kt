/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.psi.PsiElement
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.amper.plugins.schema.model.PluginDataResponse.DiagnosticKind.ErrorUnresolvedLikeConstruct
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KaTypeProjection
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.types.Variance

context(session: KaSession, _: DiagnosticsReporter, typeCollector: SymbolsCollector)
internal fun KaType.parseSchemaType(origin: () -> PsiElement): PluginData.Type? {
    val isNullable = nullability == KaTypeNullability.NULLABLE
    val symbol = expandTypeToClassSymbol() ?: run {
        reportUnexpectedType(origin)
        return null
    }
    return when (symbol.classId) {
        StandardClassIds.Boolean -> PluginData.Type.BooleanType(isNullable)
        StandardClassIds.Int -> PluginData.Type.IntType(isNullable)
        StandardClassIds.String -> PluginData.Type.StringType(isNullable)
        StandardClassIds.List -> PluginData.Type.ListType(
            elementType = typeArguments.getOrNull(0)?.parseSchemaType(
                origin = { origin().let { it.findDescendantOfType<KtTypeProjection>() ?: it } },
            ) /*invalid Kotlin*/ ?: return null,
            isNullable,
        )
        StandardClassIds.Map -> {
            val keyType = typeArguments.getOrNull(0) ?: /*invalid Kotlin*/ return null
            if (keyType !is KaTypeArgumentWithVariance || with(session) { !keyType.type.isStringType }) {
                reportError(
                    origin().let { it.findDescendantOfType<KtTypeProjection>() ?: it },
                    "schema.type.map.key.unexpected",
                )
            }
            val mapValueOrigin = {
                origin().let {
                    it.collectDescendantsOfType<KtTypeProjection>(
                        // Do not go in-depth, the order will be wrong: the `getOrNull(1)` could refer to a nested arg.
                        canGoInside = { e -> e !is KtTypeProjection },
                    ).getOrNull(1) ?: it
                }
            }
            PluginData.Type.MapType(
                valueType = typeArguments.getOrNull(1)?.parseSchemaType(
                    origin = mapValueOrigin,
                ) /*invalid Kotlin*/ ?: return null,
                isNullable,
            )
        }
        PATH_CLASS -> PluginData.Type.PathType(isNullable)
        else -> when (symbol.classKind) {
            KaClassKind.INTERFACE -> {
                if (symbol.isAnnotatedWith(SCHEMA_ANNOTATION_CLASS)) {
                    PluginData.Type.ObjectType(
                        checkNotNull(symbol.classId) { "not reachable: interface can't be anonymous" }.toSchemaName(),
                        isNullable,
                    )
                } else { reportUnexpectedType(origin); null }
            }
            KaClassKind.ENUM_CLASS -> {
                typeCollector.onEnumReferenced(symbol)
                PluginData.Type.EnumType(
                    checkNotNull(symbol.classId) { "not reachable: enum can't be anonymous" }.toSchemaName(),
                    isNullable,
                )
            }
            else -> { reportUnexpectedType(origin); null }
        }
    }
}

context(_: KaSession, _: DiagnosticsReporter, _: SymbolsCollector)
private fun KaTypeProjection.parseSchemaType(origin: () -> PsiElement): PluginData.Type? {
    return when (this) {
        is KaStarTypeProjection -> run {
            reportError(origin(), "schema.type.forbidden.projection"); null
        }
        is KaTypeArgumentWithVariance -> {
            if (variance != Variance.INVARIANT)
                reportError(origin(), "schema.type.forbidden.projection")
            type.parseSchemaType(origin)
        }
    }
}

context(_: KaSession, _: DiagnosticsReporter)
private fun KaType.reportUnexpectedType(origin: () -> PsiElement) {
    report(origin(), "schema.type.unexpected", renderToString(), kind = ErrorUnresolvedLikeConstruct)
}