/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

interface SchemaVariantDeclaration : SchemaTypeDeclaration {
    /**
     * Basically a `SchemaVariantDeclaration | SchemaObjectDeclaration` type. Nothing more.
     *
     * @see variantTree
     */
    sealed interface Variant {
        val declaration: SchemaTypeDeclaration

        data class LeafVariant(
            override val declaration: SchemaObjectDeclaration,
        ) : Variant

        data class SubVariant(
            override val declaration: SchemaVariantDeclaration,
        ) : Variant
    }

    /**
     * List of [SchemaVariantDeclaration | SchemaObjectDeclaration][Variant].
     * Preserves the tree structure.
     *
     * Example:
     * ```kotlin
     * sealed interface Super
     *
     * interface A : Super            // Leaf variant
     *
     * sealed interface Sub : Super   // Sub-variant
     *
     * interface B : Super            // Leaf variant
     * interface C : Sub              // Leaf variant
     * ```
     */
    val variantTree: List<Variant>

    /**
     * Flattened list of all [leaf variants][Variant.LeafVariant] from [variantTree].
     */
    val variants: List<SchemaObjectDeclaration>
}