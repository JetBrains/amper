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
    @Suppress("UNCHECKED_CAST")
    fun refineTree(tree: TreeValue<Merged>, selectedContexts: Contexts) =
        RefineRequest(selectedContexts, contextComparator).refine(tree) as TreeValue<Refined>
}

@TestOnly
internal fun TreeValue<Merged>.refineTree(selectedContexts: Contexts, contextComparator: ContextsInheritance<Context>) =
    RefineRequest(selectedContexts, contextComparator).refine(this) as TreeValue<Refined>

class RefineRequest(
    private val selectedContexts: Contexts,
    contextComparator: ContextsInheritance<Context>,
) : ContextsInheritance<Context> by contextComparator {

    /**
     * Creates a copy out of the subtree of the initial tree, selected by [selectedContexts]
     * with merged nodes.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : TreeValue<Merged>> refine(node: T): T {
        val node = node as TreeValue<Merged>
        return when (node) {
            is ListValue -> node.copy(children = node.children.filterByContexts().map(::refine)) as T
            is MapLikeValue -> node.copy(children = node.children.refineProperties()) as T
            is ScalarOrReference -> node as T
            is NoValue<*> -> node as T
        }
    }

    /**
     * Merge named properties, comparing them by their contexts and by their name.
     */
    private fun List<MapLikeValue.Property<TreeValue<Merged>>>.refineProperties(): List<MapLikeValue.Property<TreeValue<Merged>>> =
        filterByContexts().run {
            // Do actual overriding for key-value pairs.
            val keyValuesMerged = filterPropValueIs<ScalarOrReference<Merged>>().refineOrReduceByKeys {
                it.sortedWith(::compareAndReport).reduceProperties { first, second ->
                    val newTrace = second.trace.withPrecedingValue(first)
                    when (second) {
                        is ScalarValue -> second.copy(second.value, newTrace)
                        is ReferenceValue -> second.copy(second.value, newTrace)
                    }
                }
            }

            // Concatenate list nodes.
            val listsMerged = filterPropValueIs<ListValue<Merged>>().refineOrReduceByKeys {
                it.sortedWith(::compareAndReport).reduceProperties { first, second ->
                    second.copy(
                        children = first.children.plus(second.children).filterByContexts().map(::refine),
                        trace = second.trace.withPrecedingValue(first),
                    )
                }
            }

            // Call recursive merge for mapping nodes.
            // Note: We dont need to sort here because the order is relevant only for leaves or for lists.
            val mapsMerged = filterPropValueIs<MapLikeValue<Merged>>().refineOrReduceByKeys {
                it.reduceProperties { first, second ->
                    second.copy(
                        children = (first.children + second.children).refineProperties(),
                        trace = second.trace.withPrecedingValue(first),
                    )
                }
            }

            // For no value properties, just pick the first one.
            val noValues = filter { it.value is NoValue<*> }.refineOrReduceByKeys { it.first() }

            // Group the result.
            val refinedProperties = keyValuesMerged + mapsMerged + listsMerged

            // Restore order. Also, ignore NoValues if anything is overwriting them.
            val unordered = refinedProperties.associateBy { it.key }
            val unorderedNoValue = noValues.associateBy { it.key }
            return map { it.key }.distinct().map { unordered[it] ?: unorderedNoValue[it]!! }
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

    private fun <T : TreeValue<Merged>> List<MapLikeValue.Property<T>>.reduceProperties(block: (T, T) -> T) =
        reduce { first, second -> second.copy(second.key, second.kTrace, block(first.value, second.value)) }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : TreeValue<Merged>> List<MapLikeValue.Property<*>>.filterPropValueIs() =
        filter { it.value is T } as List<MapLikeValue.Property<T>>

    /**
     * Refines the element if it is single or applies [reduce] to a collection of properties grouped by keys.
     */
    private fun <T : MapLikeValue.Property<TreeValue<Merged>>> List<T>.refineOrReduceByKeys(reduce: (List<T>) -> T) =
        groupBy { it.key }.values.map { props ->
            props.singleOrNull()?.let { it.copy(value = refine(it.value)) }
                ?: props.filterByContexts().let(reduce)
        }

    private fun <T : WithContexts> List<T>.filterByContexts() =
        filter { selectedContexts.isMoreSpecificThan(it.contexts).sameOrMoreSpecific }
}