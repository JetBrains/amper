/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.tree.MapLikeValue.Property
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration

/**
 * This is a hint to limit compile time [TreeValue] usage.
 */
sealed interface TreeState

typealias OwnedTree = TreeValue<TreeState>

class Owned(
    override val children: MapLikeChildren<TreeState>,
    override val type: SchemaObjectDeclaration?,
    override val trace: Trace,
    override val contexts: Contexts,
) : MapLikeValue<TreeState>, TreeState

typealias MergedTree = TreeValue<Merged>

class Merged(
    val mapLikeChildren: Map<String, Property<TreeValue<Merged>>>,
    val otherChildren: MapLikeChildren<Merged>,
    override val type: SchemaObjectDeclaration?,
    override val trace: Trace,
    override val contexts: Contexts,
) : MapLikeValue<Merged>, TreeState {
    override val children = mapLikeChildren.values + otherChildren
}

typealias RefinedTree = TreeValue<Refined>

class Refined(
    val refinedChildren: Map<String, Property<TreeValue<Refined>>>,
    override val type: SchemaObjectDeclaration?,
    override val trace: Trace,
    override val contexts: Contexts,
) : MapLikeValue<Refined>, TreeState {
    override val children = refinedChildren.values.toList()
}

/**
 * Utility copy method that is aware of `MapLikeValue` hierarchy.
 */
fun <TS : TreeState> MapLikeValue<TS>.copy(
    children: MapLikeChildren<TS> = this.children,
    type: SchemaObjectDeclaration? = this.type,
    trace: Trace = this.trace,
    contexts: Contexts = this.contexts,
): MapLikeValue<TS> = when (this) {
    is Owned -> Owned(
        children = children,
        type = type,
        trace = trace,
        contexts = contexts
    )
    is Merged -> Merged(
        mapLikeChildren = (children as MapLikeChildren<Merged>).filter { it.value is MapLikeValue<*> }.associateBy { it.key },
        otherChildren = children.filter { it.value !is MapLikeValue<*> },
        type = type,
        trace = trace,
        contexts = contexts
    )
    is Refined -> Refined(
        refinedChildren = (children as MapLikeChildren<Refined>).associateBy { it.key },
        type = type,
        trace = trace,
        contexts = contexts
    )
    else -> error("Unreachable")
} as MapLikeValue<TS>

/**
 * Utility copy method that can copy only child properties with a matching value type.
 */
inline fun <TS : TreeState, reified T : TreeValue<TS>> MapLikeValue<TS>.copy(
    trace: Trace = this.trace,
    contexts: Contexts = this.contexts,
    type: SchemaObjectDeclaration? = this.type,
    crossinline transform: (key: String, pValue: T, old: Property<TreeValue<TS>>) -> MapLikeChildren<TS>?,
) = copy(
    children = children.flatMap { if (it.value is T) transform(it.key, it.value, it).orEmpty() else listOf(it) },
    trace = trace,
    contexts = contexts,
    type = type,
)