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

    @Suppress("UNCHECKED_CAST")
    fun mergeTrees(trees: List<MapLikeValue<Owned>>) = doMergeTrees(trees) as MapLikeValue<Merged>

    // TODO Optimize; Do not copy when it is unnecessary.
    private fun doMergeTrees(trees: List<MapLikeValue<Owned>>): MapLikeValue<Owned> {
        if (trees.size == 1) return trees.first().mergeSingle() as MapLikeValue<Owned>
        val firstTree = trees.first()
        // TODO Maybe check that we are merging (or within same hierarchy) types?
        val allChildren = trees.flatMap { it.children }
        val (mapLike, other) = allChildren.partitionMapLike()
        val newChildren = mapLike.mergeProperties() + other.map { it.mergeSingle() }
        val firstNonDefault = trees.firstOrNull { it.trace !is DefaultTrace } ?: firstTree
        // FIXME This is a hack for a pack of diagnostics.
        //    Should rewrite these diagnostics as [IsmDiagnosticFactory]. 
        return firstNonDefault.copy(children = newChildren, contexts = EmptyContexts)
    }

    // TODO Optimize; Do not copy when it is unnecessary.
    private fun OwnedTree.mergeSingle(): OwnedTree = when (this) {
        is ScalarValue, is ReferenceValue, is NoValue<*> -> this
        is ListValue -> copy(children = children.map { it.mergeSingle() })
        is MapLikeValue -> {
            val (mapLike, other) = children.partitionMapLike()
            val newChildren = mapLike.mergeProperties() + other.map { it.mergeSingle() }
            copy(children = newChildren, contexts = EmptyContexts)
        }
    }

    private fun MapLikeValue.Property<OwnedTree>.mergeSingle() = 
        copy(value = value.mergeSingle())

    private fun List<MapProperty<Owned>>.mergeProperties() =
        groupBy { it.key }.map { (key, group) ->
            val pType = group.first().pType // Every group has at least one element.
            val kTrace = group.first().kTrace //FIXME Need to figure out what trace is in the merged property.
            MapProperty(key, kTrace, doMergeTrees(group.map { it.value }), pType)
        }

    @Suppress("UNCHECKED_CAST")
    private fun List<MapLikeValue.Property<OwnedTree>>.partitionMapLike() = run {
        val mapLikeChildren = filter { it.value is MapLikeValue<*> } as List<MapProperty<Owned>>
        val otherChildren = filter { it.value !is MapLikeValue<*> }
        mapLikeChildren to otherChildren
    }
}