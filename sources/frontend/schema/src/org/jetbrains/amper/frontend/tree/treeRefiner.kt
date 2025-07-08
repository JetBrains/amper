/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

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
 * This is a class responsible for refining [TreeValue] values for a specified [Contexts].
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
        tree: TreeValue<Merged>, 
        selectedContexts: Contexts
    ): RefinedTree = RefineRequest(selectedContexts, contextComparator).refine(tree)
}

@TestOnly
internal fun TreeValue<*>.refineTree(
    selectedContexts: Contexts,
    contextComparator: ContextsInheritance<Context>,
): RefinedTree = RefineRequest(selectedContexts, contextComparator).refine(this)

class RefineRequest(
    private val selectedContexts: Contexts,
    contextComparator: ContextsInheritance<Context>,
) : ContextsInheritance<Context> by contextComparator {

    /**
     * Creates a copy out of the subtree of the initial tree, selected by [selectedContexts]
     * with merged nodes.
     */
    @Suppress("UNCHECKED_CAST")
    fun refine(node: TreeValue<*>): RefinedTree {
        return when (node) {
            is ListValue -> ListValue(
                children = node.children.filterByContexts().map(::refine),
                trace = node.trace,
                contexts = node.contexts,
            )
            is MapLikeValue -> Refined(
                node.children.refineProperties(),
                type = node.type,
                trace = node.trace,
                contexts = node.contexts,
            )
            is ScalarOrReference -> node as RefinedTree
            is NoValue -> node
        }
    }

    /**
     * Merge named properties, comparing them by their contexts and by their name.
     */
    private fun List<MapLikeValue.Property<TreeValue<*>>>.refineProperties(): Map<String, MapLikeValue.Property<TreeValue<Refined>>> =
        filterByContexts().run {
            // Do actual overriding for key-value pairs.
            val keyValuesMerged = filterPropValueIs<ScalarOrReference<*>>()
                .refineOrReduceByKeys {
                    it.sortedWith(::compareAndReport).reduceProperties { first, second ->
                        val newTrace = second.trace.withPrecedingValue(first)
                        val newValue = when (second) {
                            is ScalarValue -> second.copy(value = second.value, trace = newTrace)
                            is ReferenceValue -> second.copy(value = second.value, trace = newTrace)
                        }
                        newValue as ScalarOrReference<Refined>
                    }
                }

            // Concatenate list nodes.
            val listsMerged = filterPropValueIs<ListValue<*>>()
                .refineOrReduceByKeys {
                    it.sortedWith(::compareAndReport).reduceProperties { first, second ->
                        ListValue(
                            children = first.children.plus(second.children).filterByContexts().map(::refine),
                            trace = second.trace.withPrecedingValue(first),
                            contexts = second.contexts,
                        )
                    }
                }

            // Call recursive merge for mapping nodes.
            // Note: We dont need to sort here because the order is relevant only for leaves or for lists.
            val mapsMerged = filterPropValueIs<MapLikeValue<*>>()
                .refineOrReduceByKeys {
                    it.reduceProperties { first, second ->
                        Refined(
                            refinedChildren = (first.children + second.children).refineProperties(),
                            type = second.type,
                            trace = second.trace.withPrecedingValue(first),
                            contexts = second.contexts,
                        )
                    }
                }

            // For no value properties, just pick the first one.
            val noValues = filter { it.value is NoValue }
                .refineOrReduceByKeys { it.first() as MapLikeValue.Property<RefinedTree> }

            // Group the result.
            val refinedProperties = keyValuesMerged + mapsMerged + listsMerged

            // Restore order. Also, ignore NoValues if anything is overwriting them.
            val unordered = refinedProperties.associateBy { it.key }
            val unorderedNoValue = noValues.associateBy { it.key }
            return map { it.key }.distinct().associateWith { unordered[it] ?: unorderedNoValue[it]!! }
        }

    /**
     * Compares two nodes by their contexts.
     * If they are not comparable ([isMoreSpecificThan] had returned null), then the problem is reported.
     * Node is treated as "greater than" another node if its contexts can be inherited from other node contexts.
     */
    fun compareAndReport(first: MapLikeValue.Property<*>, second: MapLikeValue.Property<*>): Int =
        (first.value.contexts.isMoreSpecificThan(second.value.contexts)).asCompareResult ?: run {
            // TODO Report unable to sort. Maybe even same contexts? See [asCompareResult].
            0
        }

    // Do not call on collections without at least two elements.
    private fun <T : TreeValue<*>, R : T> List<MapLikeValue.Property<T>>.reduceProperties(block: (T, T) -> R): MapLikeValue.Property<R> {
        val initial = this[1].copy(newValue = block(this[0].value, this[1].value))
        return drop(2).fold(initial) { first, second ->
            second.copy(newValue = block(first.value, second.value))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : TreeValue<*>> List<MapLikeValue.Property<*>>.filterPropValueIs() =
        filter { it.value is T } as List<MapLikeValue.Property<T>>

    /**
     * Refines the element if it is single or applies [reduce] to a collection of properties grouped by keys.
     */
    private fun <T : MapLikeValue.Property<TreeValue<*>>> List<T>.refineOrReduceByKeys(reduce: (List<T>) -> MapLikeValue.Property<RefinedTree>) =
        groupBy { it.key }.values.map { props ->
            props.singleOrNull()?.let { MapLikeValue.Property(it.key, it.kTrace, refine(it.value), it.pType) }
                ?: props.filterByContexts().let(reduce)
        }

    private fun <T : WithContexts> List<T>.filterByContexts() =
        filter { selectedContexts.isMoreSpecificThan(it.contexts).sameOrMoreSpecific }
}