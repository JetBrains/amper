/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.util.asSafely
import java.nio.file.Path
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.isAccessible

/**
 * Key, pointing to schema value, that was connected to [PsiElement].
 */
val linkedAmperValue = Key.create<SchemaValueDelegate<*>>("org.jetbrains.amper.frontend.linkedValue")

/**
 * Key, pointing to schema enum value, that was connected to [PsiElement].
 */
val linkedAmperEnumValue = Key.create<Enum<*>>("org.jetbrains.amper.frontend.linkedEnumValue")

/**
 * Key, pointing to schema node, that was connected to [PsiElement].
 */
val linkedAmperNode = Key.create<SchemaNode>("org.jetbrains.amper.frontend.linkedNode")

typealias ValueHolders = MutableMap<String, ValueHolder<*>>

data class ValueHolder<T>(
    val value: T,
    val trace: Trace? = null,
)

@RequiresOptIn
annotation class InternalTraceSetter

/**
 * Class to collect all values registered within it.
 */
abstract class SchemaNode : Traceable {
    @IgnoreForSchema
    internal val allValues = mutableListOf<SchemaValueDelegate<*>>()
    @IgnoreForSchema
    val valueHolders: ValueHolders = mutableMapOf()

    /**
     * Register a required value (without default).
     */
    fun <T> value() = SchemaValueDelegateProvider<T>()

    /**
     * Register a value with a default.
     */
    fun <T> value(default: T) = SchemaValueDelegateProvider(Default.Static(default))

    /**
     * Register a value with a lazy default.
     */
    // the default value is nullable to allow performing validation without crashing (using "unsafe" access)
    fun <T> value(default: () -> T) = SchemaValueDelegateProvider(Default.Lambda(default))

    /**
     * Register a value with a default depending on another property
     */
    fun <T> dependentValue(property: KProperty0<T>) = SchemaValueDelegateProvider(Default.DirectDependent(property))

    /**
     * Register a value with a default depending on another property
     */
    fun <T, V> dependentValue(
        property: KProperty0<T>,
        desc: String? = null,
        transformValue: (value: T) -> V,
    ) = SchemaValueDelegateProvider(
        Default.TransformedDependent(
            desc = desc ?: "Computed from '${property.name}'",
            property = property,
            transformValue = transformValue,
        )
    )

    /**
     * Register a nullable value the given [default].
     */
    fun <T : Any> nullableValue(default: T? = null) = value(default = default)

    @IgnoreForSchema
    final override lateinit var trace: Trace
        private set

    // we have to use a setter method because custom var setters are not allowed for lateinit vars
    @InternalTraceSetter
    fun setTrace(trace: Trace) {
        if (trace is PsiTrace) {
            trace.psiElement.putUserData(linkedAmperNode, this)
        }
        this.trace = trace
    }
}

sealed class Default<out T> {
    abstract val value: T
    abstract val trace: Trace

    data class Static<T>(override val value: T) : Default<T>() {
        override val trace = DefaultTrace
    }

    data class Lambda<T>(private val getter: () -> T) : Default<T>() {
        override val value by lazy { getter() }
        override val trace = DefaultTrace
    }

    sealed class Dependent<T, V> : Default<V>() {
        abstract val property: KProperty0<T>
        abstract val desc: String

        // We need to access property.valueBase lazily because the delegate of the original property might not be
        // initialized yet. This is the case when the dependent property is declared before the one it depends on in
        // the schema.
        override val trace by lazy { DefaultTrace(computedValueTrace = property.schemaDelegate) }
    }

    data class DirectDependent<T>(
        override val property: KProperty0<T>,
    ) : Dependent<T, T>() {
        override val desc: String = "Inherited from '${property.name}'"
        override val value by lazy { property.schemaDelegate.value }
    }

    data class TransformedDependent<T, V>(
        override val desc: String,
        override val property: KProperty0<T>,
        private val transformValue: (T) -> V,
    ) : Dependent<T, V>() {
        override val value by lazy { transformValue(property.schemaDelegate.value) }
    }
}

class SchemaValueDelegateProvider<T>(
    val default: Default<T>? = null,
) : PropertyDelegateProvider<SchemaNode, SchemaValueDelegate<T>> {
    override fun provideDelegate(thisRef: SchemaNode, property: KProperty<*>): SchemaValueDelegate<T> {
        // Make sure that we can access delegates from reflection.
        property.isAccessible = true
        return SchemaValueDelegate(property, default, thisRef.valueHolders).also { thisRef.allValues.add(it) }
    }
}

/**
 * Abstract value that can have a default value.
 */
class SchemaValueDelegate<T>(
    val property: KProperty<*>,
    val default: Default<T>?,
    valueHolders: ValueHolders,
) : Traceable, ReadWriteProperty<SchemaNode, T> {
    // We are creating lambdas here to prevent misusage of [valueHolders] from [SchemaValueDelegate].
    private val valueGetter: () -> ValueHolder<T>? = { valueHolders[property.name] as ValueHolder<T>? }
    private val valueSetter: (ValueHolder<T>?) -> Unit = { if (it != null) valueHolders[property.name] = it }

    val value: T
        get() {
            val valueGetter = valueGetter()
            if (valueGetter != null) {
                return valueGetter.value
            }
            if (default != null) {
                return default.value
            }
            error("Required property '${property.name}' is not set")
        }

    // FIXME remove this, it doesn't make sense
    val unsafe: T? get() = valueGetter()?.value ?: default?.value

    val withoutDefault: ValueHolder<T>? get() = valueGetter()

    override fun getValue(thisRef: SchemaNode, property: KProperty<*>) = value
    override fun setValue(thisRef: SchemaNode, property: KProperty<*>, value: T) {
        if (value != null) {
            valueSetter(ValueHolder(value, value.asSafely<Traceable>()?.trace))
        }
    }

    override val trace: Trace
        get() = valueGetter()?.trace
            ?: default?.trace
            // Not really "default" but rather "missing mandatory value".
            // It only happens for required properties (without default) that also don't have a value.
            ?: DefaultTrace

    override fun toString(): String = "SchemaValue(property = $property, value = $value)"
}

private fun <T : KProperty<*>> T.setAccessible() = apply { isAccessible = true }

@Suppress("UNCHECKED_CAST")
fun <T, V> KProperty1<T, V>.schemaDelegate(receiver: SchemaNode): SchemaValueDelegate<V>? =
    setAccessible().getDelegate(receiver as T) as? SchemaValueDelegate<V>

/**
 * Returns the traceable [SchemaValueDelegate] of this property, or throws if this property isn't defined with such
 * a delegate. This should be used when this property is a schema property defined with a schema delegate.
 */
@Suppress("UNCHECKED_CAST")
val <T> KProperty0<T>.schemaDelegate: SchemaValueDelegate<T>
    get() = setAccessible().getDelegate() as? SchemaValueDelegate<T>
        ?: error("Property $this should have a traceable schema delegate")

val <T> KProperty0<T>.isDefault get() = schemaDelegate.trace is DefaultTrace

/**
 * Abstract class to traverse final schema tree.
 */
abstract class SchemaValuesVisitor {

    open fun visit(it: Any?) {
        when (it) {
            is Collection<*> -> visitCollection(it)
            is Map<*, *> -> visitMap(it)
            is SchemaValueDelegate<*> -> visitValue(it)
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

    open fun visitValue(it: SchemaValueDelegate<*>) {
        it.withoutDefault?.let { visit(it.value) }
    }

    open fun visitOther(it: Any?) = Unit
}

/**
 * When the enum value isn't wrapped into the schema value (e.g., in a collection or in AOM),
 * it's impossible to determine the trace of that enum.
 *
 * This wrapper allows persisting a trace in such scenarios.
 */
class TraceableEnum<T : Enum<*>>(value: T, trace: Trace) : TraceableValue<T>(value, trace) {

    init {
        if (trace is PsiTrace) trace.psiElement.putUserData(linkedAmperEnumValue, this.value)
    }

    override fun toString(): String = value.toString()
}

fun <T : Enum<*>> T.asTraceable(trace: Trace) = TraceableEnum(this, trace)
fun Path.asTraceable(trace: Trace) = TraceablePath(this, trace)
fun String.asTraceable(trace: Trace) = TraceableString(this, trace)
