/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

private fun <R, TS : TreeState> TreeVisitor<R, TS>.accept(value: TreeValue<TS>): R = when (value) {
    is ListValue<TS> -> visitListValue(value)
    is MapLikeValue<TS> -> visitMapValue(value)
    is ScalarValue<TS> -> visitScalarValue(value)
    is ReferenceValue<TS> -> visitReferenceValue(value)
    is NoValue -> visitNoValue(value)
}

/**
 * A generic [TreeValue] visitor capable of returning a result of type [R].
 */
interface TreeVisitor<R, TS : TreeState> {
    fun visitValue(value: TreeValue<TS>): R = accept(value)
    fun visitScalarValue(value: ScalarValue<TS>): R
    fun visitNoValue(value: NoValue): R
    fun visitReferenceValue(value: ReferenceValue<TS>): R
    fun visitListValue(value: ListValue<TS>): R
    fun visitMapValue(value: MapLikeValue<TS>): R
}

/**
 * A recursive implementation of [TreeVisitor] that aggregates results of each node's children (if any).
 */
abstract class RecurringTreeVisitor<R, TS : TreeState> : TreeVisitor<R, TS> {
    abstract fun aggregate(value: TreeValue<TS>, childResults: List<R>): R
    open fun aggregateList(value: ListValue<TS>, childResults: List<R>): R = aggregate(value, childResults)
    open fun aggregateMap(value: MapLikeValue<TS>, childResults: List<R>): R = aggregate(value, childResults)

    override fun visitListValue(value: ListValue<TS>): R =
        aggregateList(value, value.children.map(::visitValue))

    override fun visitMapValue(value: MapLikeValue<TS>): R =
        aggregateMap(value, value.children.map { it.value }.map(::visitValue))
}

/**
 * Convenient specification of [TreeVisitor] that returns nothing.
 */
abstract class RecurringTreeVisitorUnit<TS : TreeState> : RecurringTreeVisitor<Unit, TS>() {
    override fun aggregate(value: TreeValue<TS>, childResults: List<Unit>) = Unit
    override fun visitScalarValue(value: ScalarValue<TS>) = Unit
    override fun visitReferenceValue(value: ReferenceValue<TS>) = Unit
    override fun visitNoValue(value: NoValue) = Unit
}

/**
 * Do visit every value of the tree.
 */
fun <TS : TreeState> TreeValue<TS>.visitValues(block: (TreeValue<TS>) -> Unit) =
    object : RecurringTreeVisitorUnit<TS>() {
        override fun visitScalarValue(value: ScalarValue<TS>) = block(value)
        override fun visitReferenceValue(value: ReferenceValue<TS>) = block(value)
        override fun visitListValue(value: ListValue<TS>) = block(value).also { super.visitListValue(value) }
        override fun visitMapValue(value: MapLikeValue<TS>) = block(value).also { super.visitMapValue(value) }
    }.visitValue(this)

/**
 * Do visit every map like value of the tree.
 */
fun <TS : TreeState> TreeValue<TS>.visitMapLikeValues(block: (MapLikeValue<TS>) -> Unit) =
    object : RecurringTreeVisitorUnit<TS>() {
        override fun visitMapValue(value: MapLikeValue<TS>) = block(value).also { super.visitMapValue(value) }
    }.visitValue(this)

sealed interface TransformResult<out T>
class Changed<T>(val value: T) : TransformResult<T>
object NotChanged : TransformResult<Nothing>
object Removed : TransformResult<Nothing>

private fun <T, R> TransformResult<T>.mapIfChanged(block: (T) -> R): TransformResult<R> =
    when (this@mapIfChanged) {
        is Changed<T> -> Changed(block(value))
        is NotChanged -> this
        is Removed -> this
    }

/**
 * [TreeVisitor] implementation that is copying the tree on each node if a replacement is provided by
 * a respective `visit*` method.
 *
 * Each visit method returns `null` if nothing had changed, or transformed tree value.
 *
 * To remove a property from `MapLikeValue` or an element from `ListValue` one should
 * override `visitMapValue` or `visitListValue` respectively or return [NoValue].
 */
abstract class TreeTransformer<TS : TreeState> : TreeVisitor<TransformResult<TreeValue<TS>>, TS> {
    /**
     * Return `null` if value was removed or intact/changed value instead.
     */
    fun transform(value: TreeValue<TS>): TreeValue<TS>? =
        when (val transformed = visitValue(value)) {
            is Changed -> transformed.value
            NotChanged -> value
            Removed -> null
        }

    override fun visitNoValue(value: NoValue) = NotChanged
    override fun visitScalarValue(value: ScalarValue<TS>) = NotChanged
    override fun visitReferenceValue(value: ReferenceValue<TS>) = NotChanged

    override fun visitListValue(value: ListValue<TS>): TransformResult<TreeValue<TS>> =
        value.children.visitAll().mapIfChanged { value.copy(children = it) }

    override fun visitMapValue(value: MapLikeValue<TS>) =
        value.children.visitAll().mapIfChanged { value.copy(children = it) }

    @JvmName("acceptAllList")
    fun List<TreeValue<TS>>.visitAll(): TransformResult<List<TreeValue<TS>>> {
        val childrenTransformed = map { visitValue(it) }
        return if (childrenTransformed.all { it === NotChanged }) NotChanged
        else childrenTransformed.mapIndexedNotNull { i, it ->
            when (it) {
                is Changed -> it.value
                NotChanged -> this[i]
                Removed -> null
            }
        }.let(::Changed)
    }

    @JvmName("acceptAllMapLikeChildren")
    fun MapLikeChildren<TS>.visitAll(): TransformResult<MapLikeChildren<TS>> {
        val childrenTransformed = map { visitValue(it.value) }
        return if (childrenTransformed.all { it === NotChanged }) NotChanged
        else childrenTransformed.mapIndexedNotNull { i, it ->
            when (it) {
                is Changed -> this[i].copy(newValue = it.value)
                NotChanged -> this[i]
                Removed -> null
            }
        }.let(::Changed)
    }
}