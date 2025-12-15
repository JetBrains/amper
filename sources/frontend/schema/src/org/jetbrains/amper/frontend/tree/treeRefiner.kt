/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.api.withPrecedingValue
import org.jetbrains.amper.frontend.contexts.Context
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.ContextsInheritance
import org.jetbrains.amper.frontend.contexts.WithContexts
import org.jetbrains.amper.frontend.contexts.asCompareResult
import org.jetbrains.amper.frontend.contexts.defaultContextsInheritance
import org.jetbrains.amper.frontend.contexts.sameOrMoreSpecific
import org.jetbrains.annotations.TestOnly

/**
 * This is a class responsible for refining [TreeNode] values for a specified [Contexts].
 * Consider the following example:
 * ```yaml
 * # tree 1
 * foo:
 *   bar: myValue
 *   baz: myValue
 *
 * # tree 2
 * foo@jvm:
 *   bar@jvm: myValueJvm
 *
 * # refined tree for contexts `[jvm]`:
 * foo@jvm:
 *   bar@jvm: myValueJvm
 *   baz: myValue
 * ```
 */
class TreeRefiner(
    private val contextComparator: ContextsInheritance<Context> = defaultContextsInheritance,
) {
    fun refineTree(
        tree: MappingNode,
        selectedContexts: Contexts,
    ): RefinedMappingNode = RefineRequest(selectedContexts, contextComparator).refine(tree) as RefinedMappingNode
}

@TestOnly
internal fun MappingNode.refineTree(
    selectedContexts: Contexts,
    contextComparator: ContextsInheritance<Context>,
): RefinedMappingNode = RefineRequest(selectedContexts, contextComparator).refine(this) as RefinedMappingNode

private class RefineRequest(
    private val selectedContexts: Contexts,
    contextComparator: ContextsInheritance<Context>,
) : ContextsInheritance<Context> by contextComparator {

    /**
     * Creates a copy out of the subtree of the initial tree, selected by [selectedContexts]
     * with merged nodes.
     */
    fun refine(node: TreeNode): RefinedTreeNode {
        return when (node) {
            is RefinedTreeNode -> node
            is ListNode -> RefinedListNode(
                children = node.children.filterByContexts().map(::refine),
                type = node.type,
                trace = node.trace,
                contexts = node.contexts,
            )
            is MappingNode -> RefinedMappingNode(
                node.children.refineProperties(),
                type = node.type,
                trace = node.trace,
                contexts = node.contexts,
            )
        }
    }

    /**
     * Merge named properties, comparing them by their contexts and by their name.
     */
    private fun List<KeyValue>.refineProperties(): Map<String, RefinedKeyValue> =
        filterByContexts().run {
            // Do actual overriding for key-value pairs.
            val refinedProperties = refineOrReduceByKeys {
                it.sortedWith(::compareAndReport).reduceProperties { first: TreeNode, second: TreeNode ->
                    val newTrace = second.trace.let { trace ->
                        if (trace.isDefault) {
                            trace // Defaults with higher priority just replace each other without a trace
                        } else trace.withPrecedingValue(first)
                    }
                    when (second) {
                        is ErrorNode -> {
                            // If `first` is not an error - use it
                            // to recover as much information for the invalid but "best-effort" tree.
                            if (first is ErrorNode) ErrorNode(trace = newTrace) else refine(first)
                        }
                        is LeafTreeNode -> second.copyWithTrace(trace = newTrace)
                        is ListNode -> {
                            val firstChildren = (first as? ListNode)?.children.orEmpty()
                            RefinedListNode(
                                children = firstChildren.plus(second.children).filterByContexts().map(::refine),
                                type = second.type,
                                trace = second.trace.withPrecedingValue(first),
                                contexts = second.contexts,
                            )
                        }
                        is MappingNode -> {
                            val firstChildren = (first as? MappingNode)?.children.orEmpty()
                            val trace = second.trace.let { trace ->
                                if (trace.isDefault) {
                                    trace // Defaults with higher priority just replace each other without a trace
                                } else trace.withPrecedingValue(first)
                            }
                            RefinedMappingNode(
                                refinedChildren = (firstChildren + second.children).refineProperties(),
                                type = second.type,
                                trace = trace,
                                contexts = second.contexts,
                            )
                        }
                    }
                }
            }

            // Restore order. Also, ignore NoValues if anything is overwriting them.
            val unordered = refinedProperties.associateBy { it.key }
            return mapTo(mutableSetOf()) { it.key }.associateWith { unordered[it]!! }
        }

    /**
     * Compares two nodes by their contexts.
     * If they are not comparable ([isMoreSpecificThan] had returned null), then the problem is reported.
     * Node is treated as "greater than" another node if its contexts can be inherited from other node contexts.
     */
    fun compareAndReport(first: KeyValue, second: KeyValue): Int =
        (first.value.contexts.isMoreSpecificThan(second.value.contexts)).asCompareResult ?: run {
            // TODO AMPER-4516 Report unable to sort. Maybe even same contexts? See [asCompareResult].
            0
        }

    // Do not call on collections without at least two elements.
    private fun List<KeyValue>.reduceProperties(block: (TreeNode, TreeNode) -> RefinedTreeNode): RefinedKeyValue {
        val initial = this[1].copyWithValue(value = block(this[0].value, this[1].value))
        return drop(2).fold(initial) { first, second ->
            second.copyWithValue(block(first.value, second.value))
        }
    }

    /**
     * Refines the element if it is single or applies [reduce] to a collection of properties grouped by keys.
     */
    private fun List<KeyValue>.refineOrReduceByKeys(reduce: (List<KeyValue>) -> RefinedKeyValue) =
        groupBy { it.key }.values.map { props ->
            props.singleOrNull()?.let { it.copyWithValue(refine(it.value)) }
                ?: props.filterByContexts().let(reduce)
        }

    private fun <T : WithContexts> List<T>.filterByContexts() =
        filter { selectedContexts.isMoreSpecificThan(it.contexts).sameOrMoreSpecific }
}