/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.api.withPrecedingValue
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1


class MergeCtx<T : Any>(
    val target: T,
    val overwrite: T,
    val base: T,
)

class MergeCtxWithProp<T : Any, V>(ctx: MergeCtx<T>, prop: KProperty1<T, V>) {
    val targetProp = prop.valueBase(ctx.target)
    val baseProp = prop.valueBase(ctx.base)
    val overwriteProp = prop.valueBase(ctx.overwrite)
    val baseValue = baseProp?.withoutDefault
    val overwriteValue = overwriteProp?.withoutDefault
}

/**
 * [target] - accepted as lambda to evade non-necessary invocation.
 */
fun <T> T.mergeNode(
    overwrite: T,
    target: () -> T & Any,
    block: MergeCtx<T & Any>.() -> Unit
) = if (overwrite != null && this != null && this !== overwrite) {
    MergeCtx(target(), overwrite, this)
        .apply(block).target
        .apply { asSafely<Traceable>()?.trace = overwrite.asSafely<Traceable>()?.trace }
} else overwrite ?: this

fun <T : Any, V> MergeCtx<T>.mergeNullableCollectionProperty(
    prop: KMutableProperty1<T, List<V>?>,
) = doMergeCollection(prop) { this }

fun <T : Any, V> MergeCtx<T>.mergeCollectionProperty(
    prop: KMutableProperty1<T, List<V>>,
) = doMergeCollection(prop) { this }

private fun <T : Any, V, CV : List<V>?> MergeCtx<T>.doMergeCollection(
    prop: KMutableProperty1<T, CV>,
    toCV: List<V>.() -> CV
) = mergeProperty(prop) { toMutableList().apply { addAll(it) }.toCV() }

fun <T : Any, V> MergeCtx<T>.mergeScalarProperty(
    prop: KProperty1<T, V>
) = mergeProperty(prop) { it }

fun <T : Any, V> MergeCtx<T>.mergeProperty(
    prop: KProperty1<T, V>,
    doMerge: context(MergeCtxWithProp<T, V>) (V & Any).(V & Any) -> V,
) = with(MergeCtxWithProp(this, prop)) {
    val targetProp = targetProp ?: return@with
    when {
        baseValue != null && overwriteValue != null ->
            targetProp(doMerge.invoke(this@with, baseValue, overwriteValue), ValueBase.ValueState.MERGED, mergedTrace)

        baseValue == null && overwriteValue != null ->
            targetProp(overwriteValue, overwriteProp!!.state, explicitTrace)

        baseValue != null && overwriteValue == null ->
            targetProp(baseValue, ValueBase.ValueState.INHERITED, inheritedTrace)
    }
}

/** Trace from override value, with the preceding value as base. */
context(MergeCtxWithProp<T, V>, MergeCtx<*>) private val <T : Any, V> mergedTrace
    get() = overwriteProp!!.trace?.withPrecedingValue(precedingValue = baseProp)

/** Trace from override value, with no preceding value. */
context(MergeCtxWithProp<T, V>, MergeCtx<*>) private val <T : Any, V> explicitTrace
    get() = overwriteProp!!.trace

/** Trace from the base value, with no override. */
context(MergeCtxWithProp<T, V>, MergeCtx<*>) private val <T : Any, V> inheritedTrace
    get() = baseProp!!.trace

fun <K, V> Map<K, V>.mergeMap(overwrite: Map<K, V>?, merge: V.(V) -> V) =
    toMutableMap().apply { overwrite?.forEach { compute(it.key) { _, old -> old?.merge(it.value) ?: it.value } } }

fun <K, V> Map<K, List<V>>.mergeListsMap(overwrite: Map<K, List<V>>) =
    mergeMap(overwrite) { this + it }
