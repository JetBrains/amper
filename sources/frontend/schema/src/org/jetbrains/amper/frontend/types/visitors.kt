/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

/**
 * Basic visitor for [BuiltInTypingContext].
 */
interface ATypesVisitor<R> {
    fun SchemaType.accept(): R = visitAType(this)
    fun visitAType(type: SchemaType): R = when (type) {
        is SchemaType.EnumType -> visitEnum(type)
        is SchemaType.ScalarType -> visitScalar(type)  // FIXME: but scalar can be enum
        is SchemaType.MapType -> visitMap(type)
        is SchemaType.ListType -> visitList(type)
        is SchemaType.VariantType -> visitPolymorphic(type)
        is SchemaType.ObjectType -> visitObject(type)
    }

    fun visitEnum(type: SchemaType.EnumType): R
    fun visitScalar(type: SchemaType.ScalarType): R
    fun visitMap(type: SchemaType.MapType): R
    fun visitList(type: SchemaType.ListType): R
    fun visitPolymorphic(type: SchemaType.VariantType): R
    fun visitObject(type: SchemaType.ObjectType): R
}

/**
 * Collect all referenced [BuiltInTypingContext.Object]s starting from the given [root].
 */
fun collectReferencedObjects(root: SchemaObjectDeclaration) = collectReferencedObjects(root.toType())
fun collectReferencedObjects(type: SchemaType) = AObjectRecursiveCollector().visitAType(type)
private class AObjectRecursiveCollector : ATypesVisitor<List<SchemaType.ObjectType>> {
    private val visited = mutableSetOf<SchemaType>()
    private val empty = emptyList<SchemaType.ObjectType>()

    // Rough recursion prevention.
    override fun visitAType(type: SchemaType) = if (visited.add(type)) super.visitAType(type) else empty

    override fun visitEnum(type: SchemaType.EnumType) = empty
    override fun visitScalar(type: SchemaType.ScalarType) = empty
    override fun visitMap(type: SchemaType.MapType) = type.valueType.accept()
    override fun visitList(type: SchemaType.ListType) = type.elementType.accept()

    override fun visitPolymorphic(type: SchemaType.VariantType) =
        type.declaration.variantTree.flatMap {
            when (it) {
                is SchemaVariantDeclaration.Variant.LeafVariant -> it.declaration.toType().accept()
                is SchemaVariantDeclaration.Variant.SubVariant -> it.declaration.toType().accept()
            }
        }

    override fun visitObject(type: SchemaType.ObjectType) =
        type.declaration.properties
        .filterNot { it.isHiddenFromCompletion }.flatMap { it.type.accept() } + type
}