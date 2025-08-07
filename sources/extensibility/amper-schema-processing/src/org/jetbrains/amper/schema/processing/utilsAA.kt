/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtModifierListOwner

context(session: KaSession)
internal fun KtModifierListOwner.getAnnotation(annotation: ClassId): KtAnnotationEntry? = annotationEntries.find {
    with(session) { it.typeReference?.type?.isClassType(annotation) } == true
}

context(session: KaSession)
internal fun KtModifierListOwner.isAnnotatedWith(annotation: ClassId): Boolean =
    getAnnotation(annotation) != null

internal fun KaAnnotated.isAnnotatedWith(annotation: ClassId): Boolean =
    annotations.any { it.classId == annotation }