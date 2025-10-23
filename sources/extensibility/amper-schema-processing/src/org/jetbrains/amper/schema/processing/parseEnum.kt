/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import com.intellij.psi.PsiElement
import org.jetbrains.amper.plugins.schema.model.PluginData
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.psi
import org.jetbrains.kotlin.analysis.api.symbols.psiSafe
import org.jetbrains.kotlin.psi.KtDeclaration

context(session: KaSession)
internal fun parseEnum(symbol: KaClassSymbol) = PluginData.EnumData(
    schemaName = checkNotNull(symbol.classId) { "not reachable: enum" }.toSchemaName(),
    entries = with(session) { symbol.staticDeclaredMemberScope }.callables.filterIsInstance<KaEnumEntrySymbol>().map {
        val explicitEnumName = it.getAnnotation(ENUM_VALUE_ANNOTATION_CLASS)?.arguments?.firstOrNull()?.expression
            ?.let { v -> v as KaAnnotationValue.ConstantValue }?.value?.value as? String
        PluginData.EnumData.Entry(
            name = it.name.asString(),
            schemaName = explicitEnumName ?: it.name.asString(),
            doc = it.psiSafe<KtDeclaration>()?.getDefaultDocString(),
            origin = it.psi<PsiElement>().getSourceLocation(),
        )
    }.toList(),
    origin = symbol.psi<PsiElement>().getSourceLocation(),
)
