/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.meta

import org.jetbrains.amper.frontend.types.AmperTypes


/**
 * Basic visitor for [AmperTypes].
 */
interface ATypesVisitor<R> {
    fun AmperTypes.AmperType.accept(): R = visitAType(this)
    fun visitAType(type: AmperTypes.AmperType): R = when (type) {
        is AmperTypes.Enum -> visitEnum(type)
        is AmperTypes.Scalar -> visitScalar(type)
        is AmperTypes.Map -> visitMap(type)
        is AmperTypes.List -> visitList(type)
        is AmperTypes.Polymorphic -> visitPolymorphic(type)
        is AmperTypes.Object -> visitObject(type)
    }

    fun visitEnum(type: AmperTypes.Enum): R
    fun visitScalar(type: AmperTypes.Scalar): R
    fun visitMap(type: AmperTypes.Map): R
    fun visitList(type: AmperTypes.List): R
    fun visitPolymorphic(type: AmperTypes.Polymorphic): R
    fun visitObject(type: AmperTypes.Object): R
}

/**
 * Collect all referenced [AmperTypes.Object]s starting from the given [root].
 */
fun collectReferencedObjects(root: AmperTypes.AmperType) = AObjectRecursiveCollector().visitAType(root)
private class AObjectRecursiveCollector : ATypesVisitor<List<AmperTypes.Object>> {
    private val visited = mutableSetOf<AmperTypes.AmperType>()
    private val empty = emptyList<AmperTypes.Object>()
    
    // Rough recursion prevention.
    override fun visitAType(type: AmperTypes.AmperType) = if (visited.add(type)) super.visitAType(type) else empty
    
    override fun visitEnum(type: AmperTypes.Enum) = empty
    override fun visitScalar(type: AmperTypes.Scalar) = empty
    override fun visitMap(type: AmperTypes.Map) = type.valueType.accept()
    override fun visitList(type: AmperTypes.List) = type.valueType.accept()
    override fun visitPolymorphic(type: AmperTypes.Polymorphic) = type.inheritors.flatMap { it.accept() }
    override fun visitObject(type: AmperTypes.Object) = type.properties.flatMap { it.type.accept() } + type
}