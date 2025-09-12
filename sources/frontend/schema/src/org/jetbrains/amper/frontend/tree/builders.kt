/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.DefaultCtxs
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.frontend.types.getType
import java.nio.file.Path
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createType

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
        val type: SchemaType.ObjectType? = null,
    ) {
        internal val properties = mutableListOf<MapLikeValue.Property<*>>()

        infix fun KProperty1<out SchemaNode, *>.setTo(value: TreeValue<*>) =
            name.setTo(value)

        infix fun String.setTo(value: TreeValue<*>) =
            if (type != null) properties += MapLikeValue.Property(this, trace, value, type.declaration)
            else properties += MapLikeValue.Property(this, trace, value, null)

        @JvmName("invokeMapLike")
        inline operator fun <reified T : SchemaNode?> KProperty1<out SchemaNode, T>.invoke(noinline block: MapLikeValueBuilder.() -> Unit) =
            setTo(`object`<T>(block))

        @JvmName("invokeList")
        operator fun KProperty1<out SchemaNode, List<*>?>.invoke(
            block: MutableList<TreeValue<*>>.() -> Unit,
        ) = setTo(list(types.getType(returnType) as SchemaType.ListType, block))

        infix fun KProperty1<out SchemaNode, Map<String, *>?>.setToMap(
            block: MapLikeValueBuilder.() -> Unit,
        ) = setTo(map(types.getType(returnType) as SchemaType.MapType, block))
    }

    fun `object`(type: SchemaType.ObjectType, block: MapLikeValueBuilder.() -> Unit) =
        Owned(MapLikeValueBuilder(trace, type).apply(block).properties, type, trace, contexts)

    inline fun <reified T : SchemaNode?> `object`(noinline block: MapLikeValueBuilder.() -> Unit) =
        `object`(types.getType<T>() as SchemaType.ObjectType, block)

    fun map(type: SchemaType.MapType, block: MapLikeValueBuilder.() -> Unit) =
        Owned(MapLikeValueBuilder(trace).apply(block).properties, type, trace, contexts)

    fun list(type: SchemaType.ListType, block: MutableList<TreeValue<*>>.() -> Unit) =
        ListValue(mutableListOf<TreeValue<*>>().apply(block), type, trace, contexts)

    fun scalar(value: Enum<*>, trace: Trace = this.trace) =
        ScalarValue<Owned>(value, types.getType(value.declaringJavaClass.kotlin.createType()) as SchemaType.EnumType, trace, contexts)

    fun scalar(value: Path, trace: Trace = this.trace) =
        ScalarValue<Owned>(value, SchemaType.PathType, trace, contexts)

    fun scalar(value: Boolean, trace: Trace = this.trace) =
        ScalarValue<Owned>(value, SchemaType.BooleanType, trace, contexts)

    fun scalar(value: String, trace: Trace = this.trace) =
        ScalarValue<Owned>(value, SchemaType.StringType, trace, contexts)

    fun scalar(value: TraceableString, trace: Trace = this.trace) =
        ScalarValue<Owned>(value, SchemaType.TraceableStringType, trace, contexts)
}
