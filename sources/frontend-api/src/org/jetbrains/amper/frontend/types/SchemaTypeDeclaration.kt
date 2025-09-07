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
     * Where the declaration comes from.
     */
    val origin: SchemaOrigin
}