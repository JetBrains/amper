/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.tree.DefaultsReferenceTransform
import org.jetbrains.amper.frontend.tree.ReferenceNode
import org.jetbrains.amper.frontend.tree.RefinedTreeNode
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import java.nio.file.Path
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.isAccessible

typealias ValueHolders = MutableMap<String, ValueHolder<*>>

data class ValueHolder<T>(
    val value: T,
    val valueTrace: Trace,
    val keyValueTrace: Trace,
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
    fun <T> value(default: T) = SchemaValueDelegateProvider<T>(Default.Static(default))

    /**
     * Register a nested object value with a default-constructed instance by default.
     */
    inline fun <reified T : SchemaNode> nested() = SchemaValueDelegateProvider<T>(Default.NestedObject)

    /**
     * Register a value with a default referencing another property.
     *
     * WARNING: Only the property's `name` is really taken into account.
     * The name is resolved using regular reference resolution rules: ${<name>}
     */
    fun <T> referenceValue(property: KProperty0<T>) =
        SchemaValueDelegateProvider<T>(Default.Reference(listOf(property.name)))

    /**
     * Register a value with a default referencing another property from the same type.
     *
     * WARNING: Only the properties' `name` is really taken into account.
     * The name is resolved using regular reference resolution rules: `${<firstName>.<secondName>}`
     */
    fun <T1, T2> referenceValue(first: KProperty0<T1>, second: KProperty1<T1, T2>) =
        SchemaValueDelegateProvider<T2>(Default.Reference(listOf(first.name, second.name)))

    /**
     * Register a value with a default depending on another property
     */
    @OptIn(DefaultsReferenceTransform::class)
    fun <T> referenceValue(
        property: KProperty0<*>,
        description: String,
        transformValue: (RefinedTreeNode) -> T,
    ) = SchemaValueDelegateProvider<T>(
        Default.Reference(
            referencedPath = listOf(property.name),
            transform = ReferenceNode.Transform(
                description = description,
                function = transformValue,
            ),
        )
    )

    /**
     * Register a nullable value the given [default].
     */
    fun <T : Any> nullableValue(default: T? = null) = value(default = default)

    @IgnoreForSchema
    lateinit var schemaType: SchemaObjectDeclaration

    @IgnoreForSchema
    final override lateinit var trace: Trace
        @InternalTraceSetter
        set
}

sealed interface Default {
    /**
     * A static default [value].
     * The value must correspond to the property type.
     */
    // TODO: Make a type-safe value
    data class Static(val value: Any?) : Default

    /**
     * A default marker that means to create a default sub-object when none is provided.
     */
    data object NestedObject : Default

    /**
     * A reference default.
     */
    class Reference(
        val referencedPath: List<String>,
        val transform: ReferenceNode.Transform? = null,
    ) : Default
}

class SchemaValueDelegateProvider<T>(
    val default: Default? = null,
) : PropertyDelegateProvider<SchemaNode, SchemaValueDelegate<T>> {
    override fun provideDelegate(thisRef: SchemaNode, property: KProperty<*>): SchemaValueDelegate<T> {
        // Make sure that we can access delegates from reflection.
        property.isAccessible = true
        return SchemaValueDelegate<T>(property, default, thisRef.valueHolders).also { thisRef.allValues.add(it) }
    }
}

/**
 * Abstract value that can have a default value.
 */
class SchemaValueDelegate<T>(
    val property: KProperty<*>,
    val default: Default?,
    valueHolders: ValueHolders,
) : Traceable, ReadOnlyProperty<SchemaNode, T> {
    // We are creating lambdas here to prevent misusage of [valueHolders] from [SchemaValueDelegate].
    @Suppress("UNCHECKED_CAST") // What we put in valueHolders is checked up front
    private val valueGetter: () -> ValueHolder<T> = {
        checkNotNull(valueHolders[property.name]) {
            "Not reached: value for property '${property.name}' is not set"
        } as ValueHolder<T>
    }

    val value: T
        get() = valueGetter().value

    override fun getValue(thisRef: SchemaNode, property: KProperty<*>) = value

    override val trace: Trace
        get() = valueGetter().valueTrace

    /**
     * A trace to the whole `key: value` pair, if present.
     */
    val keyValueTrace: Trace
        get() = valueGetter().keyValueTrace

    override fun toString(): String = "SchemaValue(property = ${property.fullyQualifiedName}, value = $value)"
}

// the first "parameter" of a property is the receiver, so the containing type
private val KProperty<*>.fullyQualifiedName: String
    get() = "${parameters.firstOrNull()?.type ?: ""}.$name"

private fun <T : KProperty<*>> T.setAccessible() = apply { isAccessible = true }

fun <R, V> KProperty1<R, V>.schemaDelegate(receiver: R): SchemaValueDelegate<V> =
    setAccessible().getDelegate(receiver)?.let {
        @Suppress("UNCHECKED_CAST") // we know the delegate type can only be V by definition of SchemaValueDelegate
        it as SchemaValueDelegate<V>
    } ?: error("Property $this should have a traceable schema delegate")

/**
 * Returns the traceable [SchemaValueDelegate] of this property, or throws if this property isn't defined with such
 * a delegate. This should be used when this property is a schema property defined with a schema delegate.
 */
val <T> KProperty0<T>.schemaDelegate: SchemaValueDelegate<T>
    get() = setAccessible().getDelegate()?.let {
        @Suppress("UNCHECKED_CAST") // we know the delegate type can only be T by definition of SchemaValueDelegate
        it as SchemaValueDelegate<T>
    } ?: error("Property $this should have a traceable schema delegate")

/**
 * Whether this property is explicitly set in config files.
 */
val <T> KProperty0<T>.isExplicitlySet: Boolean
    get() = !schemaDelegate.trace.isDefault

/**
 * Whether this property is set in a template file.
 */
val <T> KProperty0<T>.isSetInTemplate: Boolean
    get() = schemaDelegate.trace.isFromTemplate

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
