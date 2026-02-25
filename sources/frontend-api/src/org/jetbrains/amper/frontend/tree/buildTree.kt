/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.DefaultContext
import org.jetbrains.amper.frontend.types.BuiltinSchemaEnumDeclarationBase
import org.jetbrains.amper.frontend.types.BuiltinSchemaObjectDeclarationBase
import org.jetbrains.amper.frontend.types.BuiltinVariantDeclarationBase
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import java.nio.file.Path
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * DSL marker for tree building functions.
 */
@DslMarker
annotation class TreeBuilding


/**
 * Build a [MappingNode] instance with the given [declaration].
 *
 * Syntax
 * ```kotlin
 * buildTree(DeclarationOfSomething) {
 *   stringProperty("hello")
 *   pathProperty(Path("/"))
 *   enumProperty(MyEnum.One)
 *   listOfPathProperty {
 *     myPaths.forEach { add(it) }
 *   }
 *   objectProperty {
 *     variantProperty(DeclarationOfSomeConcreteType) {
 *        name("my-name")
 *     }
 *     mapOfIntProperty {
 *       put["key1"](1)
 *       put["key2"](2)
 *     }
 *     mapOfObjectProperty {
 *       put["one"] {
 *         stringProperty("one")
 *       }
 *       put["two"] {
 *         stringProperty("two")
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * @param trace to set for all the nested nodes. Can be customized inside the [block] with the [withTrace] invocations.
 * @param contexts to set for all the nested nodes.
 */
inline fun <D : BuiltinSchemaObjectDeclarationBase<*>> buildTree(
    declaration: D,
    trace: Trace = DefaultTrace,
    contexts: Contexts = listOf(DefaultContext.ReactivelySet),
    block: ObjectBuilderBlock<D>,
): MappingNode {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val root = ValueSinkPoint(TypeDescriptor.Object(declaration), declaration.toType(), Unit)
    val sink = object : ValueSink<Unit> {
        lateinit var result: MappingNode
        override fun supply(point: ValueSinkPoint<*, Unit>, value: TreeNode) {
            result = value as MappingNode
        }
    }
    context(BuilderContext(trace, contexts, sink, declaration)) {
        root { block() }
    }
    return sink.result
}

/**
 * Change the [trace] for all the nodes that are created within the [block].
 */
inline fun <K, B> BuilderContext<K, B>.withTrace(
    trace: Trace,
    block: BuilderContext<K, B>.() -> Unit,
) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    copy(trace = trace).block()
}

/**
 * Pass the ready [node] when a [org.jetbrains.amper.frontend.api.CustomSchemaDeclaration] type is expected.
 * The type is checked at runtime, otherwise the [IllegalArgumentException] is thrown, unless [unsafe] is set.
 *
 * @param unsafe do not perform a runtime type check. Can lead to an inconsistent tree state.
 */
context(c: BuilderContext<K, *>)
operator fun <K> ValueSinkPoint<TypeDescriptor.CustomObject, K>.invoke(node: MappingNode, unsafe: Boolean = false) {
    if (!unsafe) {
        require((type as? SchemaType.ObjectType)?.declaration == node.declaration) {
            "Expected $type, got ${node.type}"
        }
    }
    c.sink.supply(this, node)
}

/**
 * Create and supply an [StringNode].
 */
@JvmName("invokeString")
context(c: BuilderContext<K, *>)
operator fun <K> ValueSinkPoint<TypeDescriptor.String, K>.invoke(value: String) {
    c.sink.supply(this, StringNode(value, type as SchemaType.StringType, c.trace, c.contexts))
}

/**
 * Create and supply an [PathNode].
 */
@JvmName("invokePath")
context(c: BuilderContext<K, *>)
operator fun <K> ValueSinkPoint<TypeDescriptor.Path, K>.invoke(value: Path) {
    c.sink.supply(this, PathNode(value, type as SchemaType.PathType, c.trace, c.contexts))
}

/**
 * Create and supply an [BooleanNode].
 */
@JvmName("invokeBoolean")
context(c: BuilderContext<K, *>)
operator fun <K> ValueSinkPoint<TypeDescriptor.Boolean, K>.invoke(value: Boolean) {
    c.sink.supply(this, BooleanNode(value, type as SchemaType.BooleanType, c.trace, c.contexts))
}

/**
 * Create and supply an [IntNode].
 */
@JvmName("invokeInt")
context(c: BuilderContext<K, *>)
operator fun <K> ValueSinkPoint<TypeDescriptor.Int, K>.invoke(value: Int) {
    c.sink.supply(this, IntNode(value, type as SchemaType.IntType, c.trace, c.contexts))
}

/**
 * Create and supply an [EnumNode].
 */
@JvmName("invokeEnum")
context(c: BuilderContext<K, *>)
operator fun <K, E : Enum<E>> ValueSinkPoint<TypeDescriptor.Enum<E>, K>.invoke(value: E) {
    c.sink.supply(this, EnumNode(value.name, descriptor.declaration.toType(), c.trace, c.contexts))
}

/**
 * Build and supply an object [MappingNode].
 *
 * Example:
 * ```kotlin
 * objectProperty {  // this invoke() call
 *   property1(...)
 *   property2 { ... }
 * }
 * ```
 * Inside the builder block, if one property is passed value twice or more, multiple [KeyValue]s are created.
 */
@JvmName("invokeObject")
context(c: BuilderContext<K, *>)
inline operator fun <K, D> ValueSinkPoint<TypeDescriptor.Object<D>, K>.invoke(
    block: ObjectBuilderBlock<D>,
) where D : BuiltinSchemaObjectDeclarationBase<*> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val children = mutableListOf<KeyValue>()
    val childConsumer = ValueSink { sinkPoint, node ->
        children += KeyValue(node.trace, node, sinkPoint.key, node.trace)
    }
    val newC = BuilderContext(c.trace, c.contexts, childConsumer, descriptor.declaration)
    newC.block()
    c.sink.supply(this, MappingNode(children, descriptor.declaration.toType(), c.trace, c.contexts))
}

/**
 * Pass a ready object [MappingNode] instance.
 * ```kotlin
 * val someReadyInstance = buildTree(...) { ... }
 * // ..
 * objectProperty(someReadyInstance)  // this invoke() call
 * ```
 * @throws IllegalArgumentException if the runtime type-check fails.
 */
@JvmName("invokeObject")
context(c: BuilderContext<K, *>)
operator fun <K, D> ValueSinkPoint<TypeDescriptor.Object<D>, K>.invoke(
    node: MappingNode,
) where D : BuiltinSchemaObjectDeclarationBase<*> {
    require(node.declaration == descriptor.declaration) {
        "Expected ${descriptor.declaration}, got ${node.declaration}"
    }
    c.sink.supply(this, node)
}

/**
 * Build and supply a [MappingNode] with the selecated [variant][declaration]:
 * ```kotlin
 * variantProperty(DeclarationOfSomething) {  // this invoke() call
 *    // build your object here
 * }
 * ```
 */
@JvmName("invokeVariant")
context(c: BuilderContext<K, *>)
inline operator fun <K, S, D, S1, D1> ValueSinkPoint<TypeDescriptor.Variant<S, D>, K>.invoke(
    declaration: D1,
    block: ObjectBuilderBlock<D1>,
) where S : SchemaNode, D : BuiltinVariantDeclarationBase<S>, S1 : S, D1 : BuiltinSchemaObjectDeclarationBase<S1> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    ValueSinkPoint(TypeDescriptor.Object(declaration), declaration.toType(), key).invoke(block)
}

/**
 * Pass the ready [node] when a variant type is required:
 * ```kotlin
 * val someReadyInstance = buildTree(...) { ... }
 * // ..
 * variantProperty(someReadyInstance)  // this invoke() call
 * ```
 * @throws IllegalArgumentException if the runtime type-check fails.
 */
@JvmName("invokeVariant")
context(c: BuilderContext<K, *>)
operator fun <K, S, D> ValueSinkPoint<TypeDescriptor.Variant<S, D>, K>.invoke(
    node: MappingNode,
) where S : SchemaNode, D : BuiltinVariantDeclarationBase<S> {
    require(descriptor.declaration.variants.any { it == node.declaration }) {
        "${node.declaration} is not among the variants of ${descriptor.declaration}"
    }
    c.sink.supply(this, node)
}

/**
 * Build and supply a [ListNode].
 *
 * Examples:
 * ```kotlin
 * listOfEnum { // this invoke() call
 *   add(Enum.One)
 *   add(Enum.Two)
 * }
 * listOfObject { // this invoke() call
 *   add {
 *     // build your object here
 *   }
 * }
 * ```
 *
 * @param skipIfEmpty do not supply the resulting list node if it ended up empty.
 */
@JvmName("invokeList")
context(c: BuilderContext<K, *>)
inline operator fun <K, P : TypeDescriptor> ValueSinkPoint<TypeDescriptor.List<P>, K>.invoke(
    skipIfEmpty: Boolean = false,
    block: ListBuilderBlock<P>,
) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val children = mutableListOf<TreeNode>()
    val listType = type as SchemaType.ListType
    val newC = BuilderContext(
        trace = c.trace, contexts = c.contexts,
        sink = ValueSink<Unit> { _, node -> children += node },
        builder = ListBuilder(add = ValueSinkPoint(descriptor.element, listType.elementType, Unit)),
    )
    newC.block()
    if (!skipIfEmpty || children.isNotEmpty()) {
        c.sink.supply(this, ListNode(children = children, listType, c.trace, c.contexts))
    }
}

/**
 * Build and supply a [MappingNode] (non-object).
 *
 * Examples:
 * ```kotlin
 * mapToEnumProperty {  // this invoke() call
 *   put["myKey1"](Enum.One)
 *   put["myKey2"](Enum.Two)
 * }
 * mapToObject { // this invoke() call
 *   put["one"] {
 *      // build your object here
 *   }
 * }
 * ```
 *
 * @param skipIfEmpty do not supply the resulting list node if it ended up empty.
 */
@JvmName("invokeMap")
context(c: BuilderContext<K, *>)
inline operator fun <K, P : TypeDescriptor> ValueSinkPoint<TypeDescriptor.Map<P>, K>.invoke(
    skipIfEmpty: Boolean = false,
    block: MapBuilderBlock<P>,
) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val children = mutableListOf<KeyValue>()
    val childSink = ValueSink { sinkPoint, node ->
        children += KeyValue(sinkPoint.key, node.trace, node, node.trace)
    }
    val mapType = type as SchemaType.MapType
    val newC = BuilderContext(c.trace, c.contexts, childSink, MapBuilder(descriptor.element, mapType.valueType))
    newC.block()
    if (!skipIfEmpty || children.isNotEmpty()) {
        c.sink.supply(this, MappingNode(children = children, mapType, c.trace, c.contexts))
    }
}

// region Builder API implementation

typealias ObjectBuilderContext<D> = BuilderContext<SchemaObjectDeclaration.Property, D>
typealias ListBuilderContext<P> = BuilderContext<Unit, ListBuilder<P>>
typealias MapBuilderContext<P> = BuilderContext<String, MapBuilder<P>>

typealias ObjectBuilderBlock<D> =  ObjectBuilderContext<D>.() -> Unit
typealias ListBuilderBlock<P> = ListBuilderContext<P>.() -> Unit
typealias MapBuilderBlock<P> = MapBuilderContext<P>.() -> Unit

@TreeBuilding
data class BuilderContext<K, B>(
    val trace: Trace,
    val contexts: Contexts,
    val sink: ValueSink<K>,
    val builder: B,
)

class ListBuilder<EP : TypeDescriptor>(
    val add: ValueSinkPoint<EP, Unit>,
)

class MapBuilder<EP : TypeDescriptor>(
    private val valueDescriptor: EP,
    private val elementType: SchemaType,
) {
    inner class Key {
        operator fun get(key: String) = ValueSinkPoint(valueDescriptor, elementType, key)
    }
    val put = Key()
}

/**
 * @param K a type of "key" that describes where the value is designated.
 *  [String] for map keys, [Unit] for list elements, [SchemaObjectDeclaration.Property] for object properties.
 */
fun interface ValueSink<K> {
    /**
     * Supply the value at the [point] to the builder.
     */
    fun supply(point: ValueSinkPoint<*, K>, value: TreeNode)
}

val <P : TypeDescriptor> MapBuilderContext<P>.put: MapBuilder<P>.Key
    get() = builder.put

val <P : TypeDescriptor> ListBuilderContext<P>.add: ValueSinkPoint<P, Unit>
    get() = builder.add

sealed interface TypeDescriptor {
    data class Object<D : BuiltinSchemaObjectDeclarationBase<*>>(val declaration: D) : TypeDescriptor
    data class Variant<S : SchemaNode, D : BuiltinVariantDeclarationBase<S>>(val declaration: D) : TypeDescriptor
    data class Enum<E : kotlin.Enum<E>>(val declaration: BuiltinSchemaEnumDeclarationBase<E>) : TypeDescriptor
    data class List<P : TypeDescriptor>(val element: P) : TypeDescriptor
    data class Map<P : TypeDescriptor>(val element: P) : TypeDescriptor
    data object Path : TypeDescriptor
    data object String : TypeDescriptor
    data object Boolean : TypeDescriptor
    data object Int : TypeDescriptor
    data object CustomObject : TypeDescriptor
}

/**
 * A special "point" that the value can be supplied to via the [ValueSink].
 * Examples:
 *  - Generated property value sink points
 *  - [add] sink point, supplying values to which would add element to the list
 *  - [MapBuilder.Key.get] sink point, supplying value to which would add pairs to the mapping
 */
data class ValueSinkPoint<P : TypeDescriptor, K>(
    val descriptor: P,
    val type: SchemaType,
    val key: K,
)

/** Used by generated code **/
fun <P : TypeDescriptor> ValueSinkPoint(
    descriptor: P,
    property: SchemaObjectDeclaration.Property,
) = ValueSinkPoint(descriptor, property.type, property)

// endregion