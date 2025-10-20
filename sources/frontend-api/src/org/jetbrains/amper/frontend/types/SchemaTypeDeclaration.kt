/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

interface SchemaTypeDeclaration {
    /**
     * Type's fully qualified name, using dots as separators.
     */
    val qualifiedName: String

    /**
     * Type's simple name.
     */
    val simpleName: String
        get() = qualifiedName.substringAfterLast('.')

    /**
     * Reflection name of the public API interface, if applicable.
     * `null` for builtin declarations.
     */
    val publicInterfaceReflectionName: String?
        get() = null

    /**
     * Where the declaration comes from.
     */
    val origin: SchemaOrigin

    /**
     * Creates a non-nullable type with this declaration.
     */
    fun toType(): SchemaType
}