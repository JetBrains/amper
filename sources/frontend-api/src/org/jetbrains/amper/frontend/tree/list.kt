/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.types.SchemaType

/**
 * This is a list node of a value tree that can hold indexed children.
 *
 * TODO: Technically, the unrefined list (similar to [MappingNode]) should be able to contain
 *  *multiple elements with different contexts per index*.
 *
 * There are no such cases yet, and our YAML syntax does not allow this.
 * However such cases could arise from reference resolution, if it was implemented one the un-refined trees, which
 * may be desirable as some point.
 */
sealed interface ListNode : TreeNode {
    val children: List<TreeNode>
    val type: SchemaType.ListType
}

/**
 * This is a list node of a value tree that can hold indexed children.
 * For now, has virtually the same API as [ListNode], but overrides the [children] property to contain refined nodes.
 */
interface RefinedListNode : ListNode, RefinedTreeNode {
    override val children: List<RefinedTreeNode>
}

/**
 * Creates the [org.jetbrains.amper.frontend.tree.ListNode] instance.
 */
fun ListNode(
    children: List<TreeNode>,
    type: SchemaType.ListType,
    trace: Trace,
    contexts: Contexts,
) : ListNode = ListNodeImpl(children, type, contexts, trace)

/**
 * Creates the [org.jetbrains.amper.frontend.tree.RefinedListNode] instance.
 */
fun RefinedListNode(
    children: List<RefinedTreeNode>,
    type: SchemaType.ListType,
    trace: Trace,
    contexts: Contexts,
) : RefinedListNode = RefinedListNodeImpl(children, type, contexts, trace)

/**
 * Copies the list node instance.
 */
fun ListNode.copy(
    children: List<TreeNode> = this.children,
    type: SchemaType.ListType = this.type,
    trace: Trace = this.trace,
    contexts: Contexts = this.contexts,
): ListNode = ListNode(children, type, trace, contexts)

/**
 * Copies the refined list node instance.
 */
fun RefinedListNode.copy(
    children: List<RefinedTreeNode> = this.children,
    type: SchemaType.ListType = this.type,
    trace: Trace = this.trace,
    contexts: Contexts = this.contexts,
): RefinedListNode = RefinedListNode(children, type, trace, contexts)

private class ListNodeImpl(
    override val children: List<TreeNode>,
    override val type: SchemaType.ListType,
    override val contexts: Contexts,
    override val trace: Trace,
) : ListNode

private class RefinedListNodeImpl(
    override val children: List<RefinedTreeNode>,
    override val type: SchemaType.ListType,
    override val contexts: Contexts,
    override val trace: Trace,
) : RefinedListNode