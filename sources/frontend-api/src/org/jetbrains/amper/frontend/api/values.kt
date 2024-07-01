/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.isAccessible

/**
 * Key, pointing to schema value, that was connected to [PsiElement].
 */
val linkedAmperValue = Key.create<ValueBase<*>>("org.jetbrains.amper.frontend.linkedValue")

/**
 * Key, pointing to schema enum value, that was connected to [PsiElement].
 */
val linkedAmperEnumValue = Key.create<Enum<*>>("org.jetbrains.amper.frontend.linkedEnumValue")

/**
 * Key, pointing to schema node, that was connected to [PsiElement].
 */
val linkedAmperNode = Key.create<SchemaNode>("org.jetbrains.amper.frontend.linkedNode")

/**
 * Class to collect all values registered within it.
 */
abstract class SchemaNode : Traceable {
    internal val allValues = mutableListOf<ValueBase<*>>()

    /**
     * Register a value.
     */
    internal fun <T : Any> value() = SchemaValueProvider<T>()

    /**
     * Register a value with a default.
     */
    internal fun <T : Any> value(default: T) = SchemaValueProvider(Default.Static(default))

    /**
     * Register a value with a lazy default.
     */
    // the default value is nullable to allow performing validation without crashing (using "unsafe" access)
    internal fun <T : Any> value(default: () -> T?) = SchemaValueProvider(Default.Lambda(desc = null, default))

    /**
     * Register a nullable value.
     */
    internal fun <T : Any> nullableValue() = NullableSchemaValueProvider<T>()

    /**
     * Register a nullable value with a default.
     */
    internal fun <T : Any> nullableValue(default: T?) = NullableSchemaValueProvider(Default.Static(default))

    /**
     * Register a nullable value with a lazy default.
     */
    internal fun <T : Any> nullableValue(default: () -> T?) = NullableSchemaValueProvider(Default.Lambda(desc = null, default))

    override var trace: Trace? = null
        set(value) {
            if (value is PsiTrace) value.psiElement.putUserData(linkedAmperNode, this)
            field = value
        }
}

sealed class Default<T> {
    abstract val value: T?

    data class Static<T>(override val value: T) : Default<T>()
    data class Lambda<T>(val desc: String?, private val getter: () -> T?) : Default<T>() {
        override val value by lazy { getter() }
    }
}

internal class SchemaValueProvider<T : Any>(
    val default: Default<T>? = null,
) : PropertyDelegateProvider<SchemaNode, SchemaValue<T>> {

    override fun provideDelegate(thisRef: SchemaNode, property: KProperty<*>): SchemaValue<T> =
        SchemaValue(property, default).also {
            thisRef.allValues.add(it)
        }
}

internal class NullableSchemaValueProvider<T : Any>(
    val default: Default<T?>? = null,
) : PropertyDelegateProvider<SchemaNode, NullableSchemaValue<T>> {

    override fun provideDelegate(thisRef: SchemaNode, property: KProperty<*>): NullableSchemaValue<T> =
        NullableSchemaValue(property, default).also {
            thisRef.allValues.add(it)
        }
}

/**
 * Abstract value that can have a default value.
 */
sealed class ValueBase<T>(
    val property: KProperty<*>,
    val default: Default<T>?,
) : Traceable, ReadWriteProperty<SchemaNode, T> {

    protected var myValue: T? = null

    abstract val value: T

    val unsafe: T? get() = myValue ?: default?.value

    val withoutDefault: T? get() = myValue

    /**
     * Overwrite current value, if provided value is not null.
     */
    operator fun invoke(newValue: T?): ValueBase<T> {
        if (newValue != null) {
            myValue = newValue
            if (newValue is Traceable) {
                trace = newValue.trace
            }
        }
        return this
    }

    open operator fun invoke(newValue: T?, onNull: () -> Unit): ValueBase<T> = invoke(newValue)

    override fun getValue(thisRef: SchemaNode, property: KProperty<*>) = value

    override fun setValue(thisRef: SchemaNode, property: KProperty<*>, value: T) {
        myValue = value
    }

    override var trace: Trace? = null
        set(value) {
            if (value is PsiTrace) value.psiElement.putUserData(linkedAmperValue, this)
            field = value
        }
}

fun <T, V> KProperty1<T, V>.valueBase(receiver: T): ValueBase<V>? =
    apply { isAccessible = true }.getDelegate(receiver) as? ValueBase<V>

val <T> KProperty0<T>.valueBase: ValueBase<T>? get() =
    apply { isAccessible = true }.getDelegate() as? ValueBase<T>

val <T> KProperty0<T>.withoutDefault: T? get() {
    val delegate = valueBase
    return if (delegate != null) delegate.withoutDefault else get()
}

val <T> KProperty0<T>.unsafe: T? get() {
    val delegate = valueBase
    return if (delegate != null) delegate.unsafe else get()
}

/**
 * Required (non-null) schema value.
 */
class SchemaValue<T : Any>(property: KProperty<*>, default: Default<T>?) : ValueBase<T>(property, default) {
    override val value: T
        get() = myValue ?: default?.value ?: error("No value")

    /**
     * Overwrite current value, if provided value is not null.
     * Invoke [onNull] if it is.
     */
    override operator fun invoke(newValue: T?, onNull: () -> Unit): ValueBase<T> {
        if (newValue == null) onNull() else {
            myValue = newValue
            if (newValue is Traceable) {
                trace = newValue.trace
            }
        }
        return this
    }
}

/**
 * Optional (nullable) schema value.
 */
class NullableSchemaValue<T : Any>(property: KProperty<*>, default: Default<T?>?) : ValueBase<T?>(property, default) {
    override val value: T? get() = unsafe
}

/**
 * Abstract class to traverse final schema tree.
 */
abstract class SchemaValuesVisitor {

    open fun visit(it: Any?) {
        when (it) {
            is Collection<*> -> visitCollection(it)
            is Map<*, *> -> visitMap(it)
            is ValueBase<*> -> visitValue(it)
            is SchemaNode -> visitNode(it)
            else -> visitOther(it)
        }
    }

    open fun visitCollection(it: Collection<*>) {
        it.filterNotNull().forEach { visit(it) }
    }

    open fun visitMap(it: Map<*, *>) {
        visitCollection(it.values)
    }

    open fun visitNode(it: SchemaNode) {
        it.allValues.sortedBy { it.property.name }.forEach { visit(it) }
    }

    open fun visitValue(it: ValueBase<*>) {
        it.withoutDefault?.let { visit(it) }
    }

    open fun visitOther(it: Any?) = Unit
}

/**
 * When the enum value isn't wrapped into the schema value (e.g., in a collection or in AOM),
 * it's impossible to determine the trace of that enum.
 *
 * This wrapper allows persisting a trace in such scenarios.
 */
data class TraceableEnum<T : Enum<T>>(val value: T) : Traceable {
    override var trace: Trace? = null
        set(value) {
            if (value is PsiTrace) value.psiElement.putUserData(linkedAmperEnumValue, this.value)
            field = value
        }

    override fun toString(): String = value.toString()
}

fun <T : Enum<T>> T.asTraceable(): TraceableEnum<T> = TraceableEnum(this)
