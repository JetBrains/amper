/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.meta

import org.jetbrains.amper.frontend.types.ATypes


/**
 * Basic visitor for [ATypes].
 */
interface ATypesVisitor<R> {
    fun ATypes.AType.accept(): R = visitAType(this)
    fun visitAType(type: ATypes.AType): R = when (type) {
        is ATypes.AEnum -> visitEnum(type)
        is ATypes.AScalar -> visitScalar(type)
        is ATypes.AMap -> visitMap(type)
        is ATypes.AList -> visitList(type)
        is ATypes.APolymorphic -> visitPolymorphic(type)
        is ATypes.AObject -> visitObject(type)
    }

    fun visitEnum(type: ATypes.AEnum): R
    fun visitScalar(type: ATypes.AScalar): R
    fun visitMap(type: ATypes.AMap): R
    fun visitList(type: ATypes.AList): R
    fun visitPolymorphic(type: ATypes.APolymorphic): R
    fun visitObject(type: ATypes.AObject): R
}

/**
 * Collect all referenced [ATypes.AObject]s starting from the given [root].
 */
fun collectReferencedObjects(root: ATypes.AType) = AObjectRecursiveCollector().visitAType(root)
private class AObjectRecursiveCollector : ATypesVisitor<List<ATypes.AObject>> {
    private val visited = mutableSetOf<ATypes.AType>()
    private val empty = emptyList<ATypes.AObject>()
    
    // Rough recursion prevention.
    override fun visitAType(type: ATypes.AType) = if (visited.add(type)) super.visitAType(type) else empty
    
    override fun visitEnum(type: ATypes.AEnum) = empty
    override fun visitScalar(type: ATypes.AScalar) = empty
    override fun visitMap(type: ATypes.AMap) = type.valueType.accept()
    override fun visitList(type: ATypes.AList) = type.valueType.accept()
    override fun visitPolymorphic(type: ATypes.APolymorphic) = type.inheritors.flatMap { it.accept() }
    override fun visitObject(type: ATypes.AObject) = type.properties.flatMap { it.type.accept() } + type
}