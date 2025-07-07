/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration

/**
 * This is a hint to limit compile time [TreeValue] usage.
 */
sealed interface TreeState

typealias OwnedTree = TreeValue<Owned>

class Owned(
    override val children: MapLikeChildren<Owned>,
    override val type: SchemaObjectDeclaration?,
    override val trace: Trace,
    override val contexts: Contexts,
) : MapLikeValue<Owned>, TreeState {
    override fun copy(
        children: MapLikeChildren<Owned>,
        type: SchemaObjectDeclaration?,
        trace: Trace,
        contexts: Contexts,
    ) = Owned(children, type, trace, contexts)
}

typealias MergedTree = TreeValue<Merged>

class Merged(
    val mapLikeChildren: Map<String, MapLikeValue.Property<TreeValue<Merged>>>,
    val otherChildren: MapLikeChildren<Merged>,
    override val type: SchemaObjectDeclaration?,
    override val trace: Trace,
    override val contexts: Contexts,
) : MapLikeValue<Merged>, TreeState {
    override val children get() = mapLikeChildren.values + otherChildren
    
    override fun copy(
        children: MapLikeChildren<Merged>,
        type: SchemaObjectDeclaration?,
        trace: Trace,
        contexts: Contexts,
    ): Merged {
        val (mapLike, other) = children.partition { it.value is MapLikeValue<Merged> }
        assert(mapLike.map { it.key }.distinct().size == mapLike.size) { "Duplicate keys found during copying" }
        val mapLikeAssociated = mapLike.associateBy { it.key }
        return Merged(mapLikeAssociated, other, type, trace, contexts)
    }
}

typealias RefinedTree = TreeValue<Refined>

class Refined(
    val refinedChildren: Map<String, MapLikeValue.Property<TreeValue<Refined>>>,
    override val type: SchemaObjectDeclaration?,
    override val trace: Trace,
    override val contexts: Contexts,
) : MapLikeValue<Refined>, TreeState {
    override val children get() = refinedChildren.values.toList()
    
    override fun copy(
        children: MapLikeChildren<Refined>,
        type: SchemaObjectDeclaration?,
        trace: Trace,
        contexts: Contexts,
    ) = Refined(children.associateBy { it.key }, type, trace, contexts)
}
