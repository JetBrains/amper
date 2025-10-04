/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.types.Variance
import kotlin.contracts.contract

context(session: KaSession)
internal fun KtModifierListOwner.getAnnotation(annotation: ClassId): KtAnnotationEntry? = annotationEntries.find {
    with(session) { it.typeReference?.type?.isClassType(annotation) } == true
}

context(session: KaSession)
internal fun KtModifierListOwner.isAnnotatedWith(annotation: ClassId): Boolean =
    getAnnotation(annotation) != null

internal fun KaAnnotated.isAnnotatedWith(annotation: ClassId): Boolean = annotations.contains(annotation)

internal fun KaAnnotated.getAnnotation(annotation: ClassId): KaAnnotation? =
    annotations[annotation].firstOrNull()

@OptIn(KaExperimentalApi::class)
context(session: KaSession)
internal fun KaType.renderToString() = with(session) { render(position = Variance.INVARIANT) }

internal fun KaType.expandTypeToClassSymbol(): KaClassSymbol? {
    contract { returnsNotNull() implies (this@expandTypeToClassSymbol is KaUsualClassType) }
    return when (this) {
        is KaUsualClassType -> when (val symbol = symbol) {
            is KaAnonymousObjectSymbol, is KaNamedClassSymbol -> symbol
            is KaTypeAliasSymbol -> symbol.expandedType.expandTypeToClassSymbol()
        }
        else -> null
    }
}