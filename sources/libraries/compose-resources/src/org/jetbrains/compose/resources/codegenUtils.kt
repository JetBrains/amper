/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.compose.resources

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName

internal fun ResourceType.getClassName(): ClassName = when (this) {
    ResourceType.DRAWABLE -> ClassName("org.jetbrains.compose.resources", "DrawableResource")
    ResourceType.FONT -> ClassName("org.jetbrains.compose.resources", "FontResource")
    ResourceType.STRING -> ClassName("org.jetbrains.compose.resources", "StringResource")
    ResourceType.STRING_ARRAY -> ClassName("org.jetbrains.compose.resources", "StringArrayResource")
    ResourceType.PLURAL_STRING -> ClassName("org.jetbrains.compose.resources", "PluralStringResource")
}

internal val experimentalAnnotation = AnnotationSpec.builder(
    ClassName("org.jetbrains.compose.resources", "ExperimentalResourceApi")
).build()
