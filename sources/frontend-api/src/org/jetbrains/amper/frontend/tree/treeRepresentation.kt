/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.contexts.WithContexts
import org.jetbrains.amper.frontend.tree.MapLikeValue.Property
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.aliased
import kotlin.reflect.KProperty1


/**
 * This is a hint to limit compile time [TreeValue] usage.
 */
sealed interface TreeState

/**
 * This is a tree read from a single file (template/module).
 * See `treeReader.kt`.
 */
object Owned : TreeState
typealias OwnedTree = TreeValue<Owned>

@Suppress("UNCHECKED_CAST")
fun TreeValue<*>.asOwned() = this as OwnedTree

/**
 * This is a tree merged from several files.
 * See: `treeMerger.kt`.
 */
object Merged : TreeState
typealias MergedTree = TreeValue<Merged>

@Suppress("UNCHECKED_CAST")
fun OwnedTree.trivialMerge() = this as MergedTree

/**
 * This is a tree that is refined.
 * See: [TreeRefiner].
 */
object Refined : TreeState
typealias RefinedTree = TreeValue<Refined>

@Suppress("UNCHECKED_CAST")
fun OwnedTree.trivialRefine() = this as RefinedTree

/**
 * This a value tree node base class.
 * Basically, every tree node must contain contexts and a trace.
 */
sealed interface TreeValue<TS : TreeState> : WithContexts {
    val trace: Trace
    override val contexts: Contexts

    /**
     * Copy the current node with contexts provided.
     */
    fun withContexts(contexts: Contexts): TreeValue<TS>
}

val TreeValue<*>.isDefault get() = trace is DefaultTrace

sealed interface ScalarOrReference<TS : TreeState> : TreeValue<TS> {
    val value: Any
}

/**
 * Represent no set value. E.g. `foo: ` in YAML.
 */
abstract class NoValue<TS : TreeState> private constructor() : TreeValue<TreeState> {
    companion object : NoValue<TreeState>()

    override val trace = DefaultTrace // FIXME Add NoTrace object?
    override val contexts = EmptyContexts
    override fun withContexts(contexts: Contexts) = NoValue
}

@Suppress("UNCHECKED_CAST")
val NoValue<*>.merged get() = this as TreeValue<Merged>

@Suppress("UNCHECKED_CAST")
val NoValue<*>.owned get() = this as TreeValue<Owned>

@Suppress("UNCHECKED_CAST")
val NoValue<*>.refined get() = this as TreeValue<Refined>

/** Convenient shortcut to check results of [MapLikeValue.get]*/
fun <TS : TreeState> List<TreeValue<TS>>.isEmptyOrNoValue() = isEmpty() || all { it is NoValue<*> }

/**
 * This is a scalar value tree node. E.g., string, boolean, enum or path.
 */
data class ScalarValue<TS : TreeState>(
    override val value: Any,
    override val trace: Trace,
    override val contexts: Contexts,
) : ScalarOrReference<TS> {
    override fun withContexts(contexts: Contexts) = copy(contexts = contexts)
}

inline val <TS : TreeState> TreeValue<TS>.asScalar get() = asSafely<ScalarValue<TS>>()
inline fun <reified T : Any> TreeValue<*>.scalarValue() = asScalar?.value as? T
inline fun <reified T : Any> TreeValue<*>.scalarValueOr(block: () -> T) = scalarValue() ?: block()

/**
 * This is a reference value tree node, pointing to some subtree.
 */
data class ReferenceValue<TS : TreeState>(
    override val value: String,
    override val trace: Trace,
    override val contexts: Contexts,
) : ScalarOrReference<TS> {
    /** Convenient alias */
    val referencedPath get() = value
    override fun withContexts(contexts: Contexts) = copy(contexts = contexts)
}

/**
 * This is a list node of a value tree that can hold indexed children.
 */
data class ListValue<TS : TreeState>(
    val children: List<TreeValue<TS>>,
    override val trace: Trace,
    override val contexts: Contexts,
) : TreeValue<TS> {
    override fun withContexts(contexts: Contexts) = copy(contexts = contexts)

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : TreeValue<TS>> copy(
        trace: Trace = this.trace,
        contexts: Contexts = this.contexts,
        crossinline transform: (value: T) -> List<TreeValue<TS>>?,
    ) = ListValue(
        children = children.flatMap { if (it is T) transform(it).orEmpty() else listOf(it) },
        trace = trace,
        contexts = contexts,
    )
}

inline val <TS : TreeState> TreeValue<TS>.asList get() = asSafely<ListValue<TS>>()

/**
 * This is a map-like node in a value tree that is holding either a non-typed map or a 
 * typed object, which is determined by [type] field.
 * 
 * The key difference from other nodes is that children of this one are named 
 * properties (with their own traces). The contexts of the property are equivalent to the
 * contexts of its value.
 */
data class MapLikeValue<TS : TreeState>(
    val children: MapLikeChildren<TS>,
    override val trace: Trace,
    override val contexts: Contexts,
    val type: SchemaObjectDeclaration?,
) : TreeValue<TS> {
    data class Property<out T : TreeValue<*>>(
        val key: String,
        val kTrace: Trace,
        val value: T,
        val pType: SchemaObjectDeclaration.Property?,
    ) : WithContexts {
        constructor(key: String, kTrace: Trace, value: T, parentType: SchemaObjectDeclaration) :
                this(key, kTrace, value, parentType.aliased()[key])

        constructor(value: T, kTrace: Trace, pType: SchemaObjectDeclaration.Property) :
                this(pType.name, kTrace, value, pType)

        override val contexts get() = value.contexts
    }

    override fun withContexts(contexts: Contexts) = copy(contexts = contexts)

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : TreeValue<TS>> copy(
        trace: Trace = this.trace,
        contexts: Contexts = this.contexts,
        type: SchemaObjectDeclaration? = this.type,
        crossinline transform: (key: String, pValue: T, old: Property<TreeValue<TS>>) -> MapLikeChildren<TS>?,
    ) = MapLikeValue(
        children = children.flatMap { if (it.value is T) transform(it.key, it.value, it).orEmpty() else listOf(it) },
        trace = trace,
        contexts = contexts,
        type = type,
    )
}

// Convenient aliases for typed map-like nodes.
typealias MapProperty<TS> = Property<MapLikeValue<TS>>
typealias ScalarProperty<TS> = Property<ScalarValue<TS>>
typealias ReferenceProperty<TS> = Property<ReferenceValue<TS>>
typealias MapLikeChildren<TS> = List<Property<TreeValue<TS>>>

// Convenient accessors for typed map-like nodes.
fun <TS : TreeState> MapLikeValue<TS>.get(key: String, type: SchemaObjectDeclaration.Property?) =
    children.filter { it.key == key && it.pType == type }
inline val <TS : TreeState> TreeValue<TS>.asMapLike get() = asSafely<MapLikeValue<TS>>()
inline val <TS : TreeState> List<TreeValue<TS>>.onlyMapLike get() = mapNotNull { it.asMapLike }
inline val <TS : TreeState> List<Property<TreeValue<TS>>>.values get() = map { it.value }

// Convenient accessors for named properties.
operator fun <TS : TreeState> MapLikeValue<TS>.get(key: String) = children.filter { it.key == key }
operator fun <TS : TreeState> List<MapLikeValue<TS>>.get(key: String) = flatMap { it[key] }
operator fun <TS : TreeState> MapLikeValue<TS>.get(prop: KProperty1<*, *>) = this[prop.name]
fun MapLikeValue<Refined>.single(key: String) = this[key].single()

// MapLikeValue.Property constructors.

/** Constructs a [MapLikeValue.Property] instance with [ScalarValue] inside. */
@Suppress("FunctionName")
fun <TS : TreeState> ScalarProperty(
    aProp: SchemaObjectDeclaration.Property,
    kTrace: Trace,
    value: Any,
    trace: Trace,
    contexts: Contexts,
) = ScalarProperty<TS>(aProp.name, kTrace, ScalarValue(value, trace, contexts), aProp)

/** Constructs a [MapLikeValue.Property] instance with [ReferenceValue] inside. */
@Suppress("FunctionName")
fun <TS : TreeState> ReferenceProperty(
    aProp: SchemaObjectDeclaration.Property,
    kTrace: Trace,
    referencedPath: String,
    trace: Trace,
    contexts: Contexts
) = ReferenceProperty<TS>(aProp.name, kTrace, ReferenceValue(referencedPath, trace, contexts), aProp)

/** Constructs a [MapLikeValue.Property] instance with [ReferenceValue] inside. */
@Suppress("FunctionName")
fun <TS : TreeState> MapProperty(
    aProp: SchemaObjectDeclaration.Property,
    kTrace: Trace,
    trace: Trace,
    contexts: Contexts,
    type: SchemaObjectDeclaration?,
) = MapProperty<TS>(aProp.name, kTrace, MapLikeValue(emptyList(), trace, contexts, type), aProp)