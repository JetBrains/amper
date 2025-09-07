/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.SchemaEnum
import java.nio.file.Path
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createInstance
import kotlin.reflect.jvm.isAccessible

typealias ValueHolders = MutableMap<String, ValueHolder<*>>

data class ValueHolder<T>(
    val value: T,
    val trace: Trace? = null,
)

/**
 * This setter is made public but should never be used outside the Amper frontend.
 */
@RequiresOptIn("This is an API, that mutates/alters the already set trace. " +
        "Consider setting the correct trace from the start.")
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
     * Register a nested object value with a default-constructed instance by default.
     */
    inline fun <reified T : SchemaNode> nested() = SchemaValueDelegateProvider(Default.NestedObject(T::class))

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
            desc = desc ?: "Default, computed from '${property.name}'",
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
        @InternalTraceSetter
        set
}

sealed class Default<out T> {
    abstract val value: T
    abstract val trace: Trace

    data class Static<T>(override val value: T) : Default<T>() {
        override val trace = DefaultTrace
    }

    data class NestedObject<T : SchemaNode>(
        private val kClass: KClass<T>,
    ) : Default<T>() {
        override val value by lazy { kClass.createInstance() }
        override val trace = DefaultTrace
    }

    sealed class Dependent<T, V> : Default<V>() {
        abstract val property: KProperty0<T>
        abstract val desc: String
    }

    data class DirectDependent<T>(
        override val property: KProperty0<T>,
    ) : Dependent<T, T>() {
        override val desc: String = "Default, inherited from '${property.name}'"
        // We need to access property.schemaDelegate lazily because the delegate of the original property might not be
        // initialized yet. This is the case when the dependent property is declared before the one it depends on in
        // the schema.
        override val value by lazy { property.schemaDelegate.value }
        override val trace by lazy {
            ResolvedReferenceTrace(
                description = desc,
                referenceTrace = DefaultTrace,
                resolvedValue = property.schemaDelegate,
            )
        }
    }

    data class TransformedDependent<T, V>(
        override val desc: String,
        override val property: KProperty0<T>,
        private val transformValue: (T) -> V,
    ) : Dependent<T, V>() {
        // We need to access property.schemaDelegate lazily because the delegate of the original property might not be
        // initialized yet. This is the case when the dependent property is declared before the one it depends on in
        // the schema.
        override val value by lazy { transformValue(property.schemaDelegate.value) }
        override val trace by lazy {
            TransformedValueTrace(description = desc, sourceValue = property.schemaDelegate)
        }
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
    @Suppress("UNCHECKED_CAST") // What we put in valueHolders is checked up front
    private val valueGetter: () -> ValueHolder<T>? = { valueHolders[property.name] as ValueHolder<T>? }
    private val valueSetter: (ValueHolder<T>?) -> Unit = { if (it != null) valueHolders[property.name] = it }

    val value: T
        get() {
            val valueGetter = valueGetter()
            if (valueGetter != null) {
                return valueGetter.value
            }
            if (default is Default.TransformedDependent<*, *>) {
                // The only default that is taken into account on the delegate level
                // other defaults are merged on the tree level to make them traceable and referencable.
                return default.value
            }
            error("Required property '${property.name}' is not set")
        }

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

/**
 * Whether this property is explicitly set in config files.
 */
val <T> KProperty0<T>.isExplicitlySet: Boolean
    get() = !schemaDelegate.trace.isDefault

val SchemaNode.propertyDelegates: List<SchemaValueDelegate<*>> get() = allValues

/**
 * Abstract class to traverse final schema tree.
 */
abstract class SchemaValuesVisitor {

    open fun visit(value: Any?) {
        when (value) {
            is Collection<*> -> visitCollection(value)
            is Map<*, *> -> visitMap(value)
            is SchemaValueDelegate<*> -> visitSchemaValueDelegate(value)
            is SchemaNode -> visitSchemaNode(value)
            is TraceableValue<*> -> visitTraceableValue(value)
            is SchemaEnum -> visitSchemaEnumValue(value)
            null,
            is Boolean,
            is Short,
            is Int,
            is Long,
            is Float,
            is Double,
            is String,
            is Enum<*>,
            is Path -> visitPrimitiveLike(value)
            else -> visitOther(value)
        }
    }

    open fun visitPrimitiveLike(other: Any?) = Unit

    open fun visitSchemaEnumValue(schemaEnum: SchemaEnum) {
        visit(schemaEnum.schemaValue)
    }

    open fun visitCollection(collection: Collection<*>) {
        collection.forEach { visit(it) }
    }

    open fun visitMap(map: Map<*, *>) {
        visitCollection(map.values)
    }

    open fun visitSchemaNode(node: SchemaNode) {
        node.allValues.sortedBy { it.property.name }.forEach { visit(it) }
    }

    open fun visitSchemaValueDelegate(schemaValue: SchemaValueDelegate<*>) {
        visit(schemaValue.value)
    }

    open fun visitTraceableValue(traceableValue: TraceableValue<*>) {
        visit(traceableValue.value)
    }

    open fun visitOther(other: Any) {
        error("Type ${other::class.simpleName} is not supported in the schema")
    }
}
