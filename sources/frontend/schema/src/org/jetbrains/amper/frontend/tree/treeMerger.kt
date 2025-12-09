/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.api.withPrecedingValue
import org.jetbrains.amper.frontend.contexts.EmptyContexts

/**
 * Merges all non-null trees from the argument list.
 *
 * @see mergeTrees
 */
fun mergeTreesNotNull(vararg trees: MappingNode?) = mergeTrees(trees.filterNotNull())

/**
 * Merges all the trees from the argument list.
 */
fun mergeTrees(vararg trees: MappingNode) = mergeTrees(trees.toList())

/**
 * Merges (joins) all given [MappingNode]s into a single value.
 * The [trace][MappingNode.trace] is merged by adding each tree's trace as a preceding value.
 *
 * NOTE: The resulting tree node will have no contexts.
 *
 * @param trees input trees to merge. Must not be empty.
 */
fun mergeTrees(trees: List<MappingNode>): MappingNode {
    require(trees.isNotEmpty()) { "Cannot merge empty list of trees" }
    if (trees.size == 1) return trees.single()

    val allChildren = trees.flatMap { it.children }
    val trace = trees.fold(DefaultTrace as Trace) { acc, tree ->
        if (acc.isDefault) tree.trace else acc.withPrecedingValue(tree)
    }
    return MappingNode(
        children = allChildren,
        // TODO Maybe check that we are merging (or within same hierarchy) types?
        type = trees.first().type,
        trace = trace,
        // Note: all the children already have the necessary contexts
        contexts = EmptyContexts,
    )
}