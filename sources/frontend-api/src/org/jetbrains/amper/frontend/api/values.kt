/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.CompleteListNode
import org.jetbrains.amper.frontend.tree.CompleteMapNode
import org.jetbrains.amper.frontend.tree.CompleteObjectNode
import org.jetbrains.amper.frontend.tree.CompletePropertyKeyValue
import org.jetbrains.amper.frontend.tree.CompleteTreeNode
import org.jetbrains.amper.frontend.tree.DefaultsReferenceTransform
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.IntNode
import org.jetbrains.amper.frontend.tree.NullLiteralNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.ReferenceNode
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.tree.declaration
import org.jetbrains.amper.frontend.tree.enumConstantIfAvailable
import org.jetbrains.amper.frontend.types.SchemaType
import java.nio.file.Path
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.KProperty1

/**
 * Class to collect all values registered within it.
 */
abstract class SchemaNode : Traceable {
    private val delegates = mutableMapOf<String, SchemaValueDelegate<*>>()

    @IgnoreForSchema
    lateinit var backingTree: CompleteObjectNode
        private set

    /**
     * Register a required value (without default).
     */
    fun <T> value() = delegateProvider<T>(default = null)

    /**
     * Register a value with a default.
     */
    fun <T> value(default: T) = delegateProvider<T>(Default.Static(default))

    /**
     * Register a nested object value with a default-constructed instance by default.
     */
    fun <T : SchemaNode> nested() = delegateProvider<T>(Default.NestedObject)

    /**
     * Register a value with a default referencing another property.
     *
     * WARNING: Only the property's `name` is really taken into account.
     * The name is resolved using regular reference resolution rules: ${<name>}
     */
    fun <T> referenceValue(property: KProperty0<T>) =
        delegateProvider<T>(Default.Reference(listOf(property.name)))

    /**
     * Register a value with a default referencing another property from the same type.
     *
     * WARNING: Only the properties' `name` is really taken into account.
     * The name is resolved using regular reference resolution rules: `${<firstName>.<secondName>}`
     */
    fun <T1, T2> referenceValue(first: KProperty0<T1>, second: KProperty1<T1, T2>) =
        delegateProvider<T2>(Default.Reference(listOf(first.name, second.name)))

    /**
     * Register a value with a default depending on another property
     */
    @OptIn(DefaultsReferenceTransform::class)
    fun <T> referenceValue(
        property: KProperty0<*>,
        description: String,
        transformValue: ReferenceNode.TransformFunction<T>,
    ) = delegateProvider<T>(
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
    final override val trace: Trace
        get() = backingTree.trace

    /**
     * Called by the [CompleteObjectNode.instance] in order to set itself.
     */
    internal fun initialize(tree: CompleteObjectNode) {
        check(!::backingTree.isInitialized) { "initialize can't be called twice" }
        backingTree = tree
    }

    fun getDelegate(propertyName: String) = checkNotNull(delegates[propertyName]) {
        "Property `$propertyName` is not found in ${backingTree.declaration.qualifiedName}"
    }

    private fun <T> delegateProvider(
        default: Default?,
    ): PropertyDelegateProvider<SchemaNode, SchemaValueDelegate<T>> =
        SchemaValueDelegateProvider(default)

    private inner class SchemaValueDelegateProvider<T>(
        val default: Default?,
    ) : PropertyDelegateProvider<SchemaNode, SchemaValueDelegate<T>> {
        override fun provideDelegate(thisRef: SchemaNode, property: KProperty<*>): SchemaValueDelegate<T> {
            return SchemaValueDelegate<T>(property.name, default, thisRef).also {
                delegates[property.name] = it
            }
        }
    }
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

/**
 * Abstract value that can have a default value.
 */
class SchemaValueDelegate<T>(
    val name: String,
    val default: Default?, // Only accessed at the instrumentation stage
    val owner: SchemaNode,
) : Traceable, ReadOnlyProperty<SchemaNode, T> {
    @Suppress("UNCHECKED_CAST") // asValue() reads the SchemaType which knows the runtime type.
    val value: T by lazy {
        keyValue.value.asValue() as T
    }

    override fun getValue(thisRef: SchemaNode, property: KProperty<*>) = value

    override val trace: Trace
        get() = keyValue.value.trace

    /**
     * A trace to the whole `key: value` pair, if present.
     */
    val keyValueTrace: Trace
        get() = keyValue.trace

    override fun toString(): String =
        "SchemaValue(property = ${owner.backingTree.declaration.qualifiedName}.${name}, value = $value)"

    private val keyValue: CompletePropertyKeyValue
        get() = checkNotNull(owner.backingTree.refinedChildren[name]) {
            "Not reached: value for property '${name}' is not set"
        }
}

fun SchemaValueDelegate<String>.asTraceableValue() = TraceableString(value, trace)

fun SchemaValueDelegate<Path>.asTraceableValue() = TraceablePath(value, trace)

/**
 * Whether this was explicitly set in config files.
 */
val Traceable.isExplicitlySet: Boolean
    get() = !trace.isDefault

/**
 * Whether this was set in a template file.
 */
val Traceable.isSetInTemplate: Boolean
    get() = trace.isFromTemplate

private fun CompleteTreeNode.asValue(): Any? = when (this) {
    is BooleanNode -> value
    is IntNode -> value
    is EnumNode -> enumConstantIfAvailable?.wrapTraceable(type, trace)
        // Objects of user-defined types have no internal runtime types,
        // so they are all instantiated as `ExtensionSchemaNode`, which has no properties,
        // thus user-defined enums are not reachable for instantiation.
        ?: error("Not reached: enum with no runtime type can't be instantiated")
    is StringNode -> value.wrapTraceable(type, trace)
    is PathNode -> value.wrapTraceable(type, trace)
    is CompleteListNode -> children.map { it.asValue() }
    is CompleteMapNode ->  children.associate {
        it.key.wrapTraceable(type.keyType, it.keyTrace) to it.value.asValue()
    }
    is CompleteObjectNode -> instance
    is NullLiteralNode -> null
}

private fun Enum<*>.wrapTraceable(type: SchemaType.EnumType, trace: Trace) =
    if (type.isTraceableWrapped) asTraceable(trace) else this

private fun Path.wrapTraceable(type: SchemaType.PathType, trace: Trace) =
    if (type.isTraceableWrapped) asTraceable(trace) else this

private fun String.wrapTraceable(type: SchemaType.StringType, trace: Trace) =
    if (type.isTraceableWrapped) asTraceable(trace) else this
