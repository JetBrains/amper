/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree


import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.DefaultCtxs
import org.jetbrains.amper.frontend.types.AmperTypes
import kotlin.reflect.KProperty1


fun <R : OwnedTree> syntheticBuilder(
    types: AmperTypes,
    trace: Trace,
    contexts: Contexts = DefaultCtxs,
    block: SyntheticBuilder.() -> R,
) = SyntheticBuilder(types, trace, contexts).run(block)

class SyntheticBuilder(
    val types: AmperTypes,
    val trace: Trace,
    val contexts: Contexts,
) {
    inner class MapLikeValueBuilder(val type: AmperTypes.Object, val trace: Trace) {
        internal val properties = mutableListOf<MapLikeValue.Property<OwnedTree>>()

        infix fun KProperty1<out SchemaNode, *>.setTo(value: OwnedTree) =
            properties.add(MapLikeValue.Property(name, trace, value, type))

        @JvmName("invokeMapLike")
        inline operator fun <reified T : SchemaNode> KProperty1<out SchemaNode, T>.invoke(noinline block: MapLikeValueBuilder.() -> Unit) =
            setTo(mapLike<T>(block))

        @JvmName("invokeList")
        operator fun KProperty1<out SchemaNode, List<*>?>.invoke(block: MutableList<OwnedTree>.() -> Unit) =
            setTo(list(block))
    }

    fun mapLike(type: AmperTypes.Object, block: MapLikeValueBuilder.() -> Unit) =
        MapLikeValue(MapLikeValueBuilder(type, trace).apply(block).properties, trace, contexts, type)

    inline fun <reified T : SchemaNode> mapLike(noinline block: MapLikeValueBuilder.() -> Unit) =
        mapLike(types<T>(), block)

    fun list(block: MutableList<OwnedTree>.() -> Unit) =
        ListValue(mutableListOf<OwnedTree>().apply(block), trace, contexts)

    fun scalar(value: Any) = ScalarValue<Owned>(value, trace, contexts)
}