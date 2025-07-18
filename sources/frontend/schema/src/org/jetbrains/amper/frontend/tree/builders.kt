/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.DefaultCtxs
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.getDeclaration
import kotlin.reflect.KProperty1

fun <R : TreeValue<*>> syntheticBuilder(
    types: SchemaTypingContext,
    trace: Trace,
    contexts: Contexts = DefaultCtxs,
    block: SyntheticBuilder.() -> R,
) = SyntheticBuilder(types, trace, contexts).run(block)

class SyntheticBuilder(
    val types: SchemaTypingContext,
    val trace: Trace,
    val contexts: Contexts,
) {
    inner class MapLikeValueBuilder(
        val trace: Trace,
        val type: SchemaObjectDeclaration? = null,
    ) {
        internal val properties = mutableListOf<MapLikeValue.Property<*>>()

        infix fun KProperty1<out SchemaNode, *>.setTo(value: TreeValue<*>) =
            name.setTo(value)

        infix fun String.setTo(value: TreeValue<*>) =
            if (type != null) properties += MapLikeValue.Property(this, trace, value, type)
            else properties += MapLikeValue.Property(this, trace, value, null)

        @JvmName("invokeMapLike")
        inline operator fun <reified T : SchemaNode> KProperty1<out SchemaNode, T>.invoke(noinline block: MapLikeValueBuilder.() -> Unit) =
            setTo(`object`<T>(block))

        @JvmName("invokeList")
        operator fun KProperty1<out SchemaNode, List<*>?>.invoke(block: MutableList<TreeValue<*>>.() -> Unit) =
            setTo(list(block))
    }

    fun `object`(type: SchemaObjectDeclaration, block: MapLikeValueBuilder.() -> Unit) =
        Owned(MapLikeValueBuilder(trace, type).apply(block).properties, type, trace, contexts)

    inline fun <reified T : SchemaNode> `object`(noinline block: MapLikeValueBuilder.() -> Unit) =
        `object`(types.getDeclaration<T>(), block)

    fun map(block: MapLikeValueBuilder.() -> Unit) =
        Owned(MapLikeValueBuilder(trace).apply(block).properties, null, trace, contexts)

    fun list(block: MutableList<TreeValue<*>>.() -> Unit) =
        ListValue(mutableListOf<TreeValue<*>>().apply(block), trace, contexts)

    fun scalar(value: Any) = ScalarValue<Owned>(value, trace, contexts)
}