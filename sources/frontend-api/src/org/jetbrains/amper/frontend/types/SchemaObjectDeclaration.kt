/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.schema.ProductType
import kotlin.reflect.KClass

interface SchemaObjectDeclaration : SchemaTypeDeclaration {
    val properties: List<Property>
    fun createInstance(): SchemaNode

    data class Property(
        val name: String,
        val type: SchemaType,
        val documentation: String? = null,
        val aliases: Set<String> = emptySet(),
        val default: Default<*>? = null,
        val isModifierAware: Boolean = false,
        val isCtorArg: Boolean = false,
        val specificToPlatforms: Set<Platform> = emptySet(),
        val specificToProducts: Set<ProductType> = emptySet(),
        val isPlatformAgnostic: Boolean = false,
        val specificToGradleMessage: String? = null,
        val knownStringValues: Set<String> = emptySet(),
        val hasShorthand: Boolean = false,
    )
}