/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.contexts.WithContexts

/**
 * The tree node sealed interface.
 *
 * The [mapping nodes][MappingNode] of the tree may contain [key-value][KeyValue] pairs
 * with the same key but different contexts.
 *
 * To get a tree for specific contexts, the tree must be "refined".
 * The resulting tree consists of [RefinedTreeNode]s.
 *
 * As [RefinedTreeNode] extends the [TreeNode], the unrefined trees *may* physically contain refined nodes;
 * This may lead to refined mappings being copied as unrefined ones,
 * but that is usually what is already expected when working with the base tree node interface.
 */
sealed interface TreeNode : WithContexts, Traceable

/**
 * The refined tree node sealed interface.
 *
 * The [mapping nodes][RefinedMappingNode] of the tree contain [key-value][RefinedKeyValue] pairs with unique keys.
 */
sealed interface RefinedTreeNode : TreeNode

/**
 * A leaf tree value that cannot have any children.
 * As such, it can be used both in refined and non-refined trees.
 */
sealed interface LeafTreeNode : TreeNode, RefinedTreeNode

fun TreeNode.copyWithTrace(
    trace: Trace,
): TreeNode = when (this) {
    is RefinedTreeNode -> copyWithTrace(trace = trace)
    is ListNode -> copy(trace = trace)
    is MappingNode -> copy(trace = trace)
}

fun RefinedTreeNode.copyWithTrace(
    trace: Trace,
): RefinedTreeNode = when (this) {
    is RefinedListNode -> copy(trace = trace)
    is RefinedMappingNode -> copy(trace = trace)
    is LeafTreeNode -> copyWithTrace(trace = trace)
}

fun LeafTreeNode.copyWithTrace(
    trace: Trace,
): LeafTreeNode = when (this) {
    is ErrorNode -> ErrorNode(trace = trace)
    is ReferenceNode -> ReferenceNode(referencedPath, type, transform, trace, contexts)
    is StringInterpolationNode -> StringInterpolationNode(parts, type, trace, contexts)
    is NullLiteralNode -> NullLiteralNode(trace, contexts)
    is BooleanNode -> BooleanNode(value, type, trace, contexts)
    is EnumNode -> EnumNode(entryName, type, trace, contexts)
    is IntNode -> IntNode(value, type, trace, contexts)
    is PathNode -> PathNode(value, type, trace, contexts)
    is StringNode -> StringNode(value, type, trace, contexts)
}
