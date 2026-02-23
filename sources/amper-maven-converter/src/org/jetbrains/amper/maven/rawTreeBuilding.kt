/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.tree.BuilderContext
import org.jetbrains.amper.frontend.tree.KeyValue
import org.jetbrains.amper.frontend.tree.ListNode
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.types.SchemaType

@DslMarker
internal annotation class SimpleTreeBuilding

internal class SimpleTreeNodeFactory(
    val trace: Trace,
    val contexts: Contexts,
)

context(c: BuilderContext<K, B>)
internal inline fun <K, B, R> buildRawTree(
    block: context(SimpleTreeNodeFactory) () -> R,
) : R = context(SimpleTreeNodeFactory(c.trace, c.contexts)) { block() }

context(f: SimpleTreeNodeFactory)
internal fun scalar(value: String) = StringNode(value, SchemaType.StringType, f.trace, f.contexts)

context(f: SimpleTreeNodeFactory)
internal inline fun mapping(block: SimpleMappingBuilder.() -> Unit) = SimpleMappingBuilder().apply(block).build()

context(f: SimpleTreeNodeFactory)
internal inline fun list(block: SimpleListBuilder.() -> Unit) = SimpleListBuilder().apply(block).build()

@SimpleTreeBuilding
internal class SimpleMappingBuilder {
    private val children = mutableListOf<KeyValue>()

    context(f: SimpleTreeNodeFactory)
    fun put(key: String, value: TreeNode) {
        children += KeyValue(key, f.trace, value, f.trace)
    }

    context(f: SimpleTreeNodeFactory)
    fun build(): MappingNode =
        MappingNode(children, SchemaType.MapType(SchemaType.StringType, SchemaType.StringType), f.trace, f.contexts)
}

@SimpleTreeBuilding
internal class SimpleListBuilder {
    private val children = mutableListOf<TreeNode>()

    fun add(value: TreeNode) { children += value }

    context(f: SimpleTreeNodeFactory)
    fun build(): ListNode =
        ListNode(children, SchemaType.ListType(SchemaType.StringType), f.trace, f.contexts)
}