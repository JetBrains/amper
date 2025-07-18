/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.contexts.EmptyContexts

/**
 * This is a class responsible for merging several trees into one, moving [MapLikeValue.Property]s
 * as low in the final tree as possible.
 *
 * Note: [MergedTree] has an interesting property: If any value is overridden during refinement,
 * than overriding value is located within the same parent. Thus, any subtree can be refined
 * independently of other non-intersecting subtrees.
 *
 * Consider the following example:
 * ```yaml
 * # tree 1
 * foo:
 *   bar:
 *     baz: 42
 *
 * # tree 2
 * foo@jvm:
 *   bar@jvm:
 *     baz@jvm: 43
 *
 * # merged tree:
 * foo:
 *   bar:
 *     baz: 42
 *     baz@jvm: 43
 * ```
 */
class TreeMerger() {

    fun mergeTrees(trees: List<MapLikeValue<*>>): Merged = doMergeTrees(trees) as Merged

    fun mergeTrees(tree: MapLikeValue<*>): Merged = tree.mergeSingle() as Merged

    // TODO Optimize; Do not copy when it is unnecessary.
    private fun doMergeTrees(trees: List<MapLikeValue<*>>): MapLikeValue<Merged> {
        if (trees.size == 1) return trees.first().mergeSingle() as MapLikeValue<Merged>
        val firstTree = trees.first()
        // TODO Maybe check that we are merging (or within same hierarchy) types?
        val allChildren = trees.flatMap { it.children }
        val (mapLike, other) = allChildren.partitionMapLike()
        val otherMerged = other.map { it.mergeSingle() }
        val mapLikeMerged = mapLike.mergeProperties().associateBy { it.key }
        val firstNonDefault = trees.firstOrNull { it.trace !is DefaultTrace } ?: firstTree
        return Merged(
            mapLikeChildren = mapLikeMerged,
            otherChildren = otherMerged,
            type = firstNonDefault.type,
            trace = firstNonDefault.trace,
            contexts = EmptyContexts,
        )
    }

    // TODO Optimize; Do not copy when it is unnecessary.
    private fun TreeValue<*>.mergeSingle(): MergedTree = when (this) {
        is ScalarValue, is ReferenceValue, is NoValue -> this as MergedTree
        is ListValue -> ListValue(children.map { it.mergeSingle() }, trace, contexts) as MergedTree
        is MapLikeValue -> {
            val (mapLike, other) = children.partitionMapLike()
            val mapLikeMerged = mapLike.mergeProperties().associateBy { it.key }
            val otherMerged = other.map { it.mergeSingle() }
            Merged(mapLikeMerged, otherMerged, type, trace, EmptyContexts)
        }
    }

    private fun MapLikeValue.Property<*>.mergeSingle() =
        MapLikeValue.Property(key, kTrace, value.mergeSingle(), pType)

    private fun List<MapProperty<*>>.mergeProperties() = groupBy { it.key }.map { (key, group) ->
        val pType = group.first().pType // Every group has at least one element.
        val kTrace = group.first().kTrace //FIXME Need to figure out what trace is in the merged property.
        MapProperty(key, kTrace, doMergeTrees(group.map { it.value }), pType)
    }

    @Suppress("UNCHECKED_CAST")
    private fun List<MapLikeValue.Property<*>>.partitionMapLike() = run {
        val mapLikeChildren = filter { it.value is MapLikeValue<*> } as List<MapProperty<*>>
        val otherChildren = filter { it.value !is MapLikeValue<*> }
        mapLikeChildren to otherChildren
    }
}