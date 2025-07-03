/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.meta

import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.toType

/**
 * Basic visitor for [SchemaTypingContext].
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
 * Collect all referenced [SchemaTypingContext.Object]s starting from the given [root].
 */
fun collectReferencedObjects(root: SchemaObjectDeclaration) = AObjectRecursiveCollector().visitAType(root.toType())
private class AObjectRecursiveCollector : ATypesVisitor<List<SchemaType.ObjectType>> {
    private val visited = mutableSetOf<SchemaType>()
    private val empty = emptyList<SchemaType.ObjectType>()
    
    // Rough recursion prevention.
    override fun visitAType(type: SchemaType) = if (visited.add(type)) super.visitAType(type) else empty
    
    override fun visitEnum(type: SchemaType.EnumType) = empty
    override fun visitScalar(type: SchemaType.ScalarType) = empty
    override fun visitMap(type: SchemaType.MapType) = type.valueType.accept()
    override fun visitList(type: SchemaType.ListType) = type.elementType.accept()
    override fun visitPolymorphic(type: SchemaType.VariantType) = type.declaration.variants.flatMap {
        it.toType().accept()
    }
    override fun visitObject(type: SchemaType.ObjectType) = type.declaration.properties.flatMap { it.type.accept() } + type
}