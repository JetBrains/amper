/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.contexts.WithContexts
import org.jetbrains.amper.frontend.tree.MapLikeValue.Property
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import kotlin.reflect.KProperty1

/**
 * This a value tree node base class.
 * Basically, every tree node must contain contexts and a trace.
 */
sealed interface TreeValue<out TS : TreeState> : WithContexts, Traceable {
    override val trace: Trace
    override val contexts: Contexts

    /**
     * Copy the current node with contexts provided.
     */
    fun withContexts(contexts: Contexts): TreeValue<TS>
}

sealed interface ScalarOrReference<out TS : TreeState> : TreeValue<TS>

/**
 * Represents an invalid value that was unable to be parsed.
 */
class ErrorValue(
    /**
     * If there is no PSI element at all for the (absent) value, this trace points to the key-value element that
     * contains no value (E.g. `foo: ` in YAML).
     */
    override val trace: Trace,
) : TreeValue<Nothing> {
    override val contexts = EmptyContexts
    override fun withContexts(contexts: Contexts) = this
}

/**
 * Represents the null literal.
 */
data class NullValue<out TS : TreeState>(
    override val trace: Trace,
    override val contexts: Contexts,
) : ScalarOrReference<TS> {
    override fun withContexts(contexts: Contexts) = copy(contexts = contexts)
}

/** Convenient shortcut to check results of [MapLikeValue.get]*/
fun <TS : TreeState> List<TreeValue<TS>>.isEmptyOrNoValue() = isEmpty() || all { it is ErrorValue }

/**
 * This is a scalar value tree node. E.g., string, boolean, enum or path.
 */
data class ScalarValue<out TS : TreeState>(
    val value: Any,
    val type: SchemaType.ScalarType,
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
data class ReferenceValue<out TS : TreeState>(
    val referencedPath: List<String>,
    val type: SchemaType,
    override val trace: Trace,
    override val contexts: Contexts,
) : ScalarOrReference<TS> {
    init {
        require(referencedPath.isNotEmpty()) { "`referencePath` can't be empty" }
    }
    override fun withContexts(contexts: Contexts) = copy(contexts = contexts)
}

data class StringInterpolationValue<out TS : TreeState>(
    val parts: List<Part>,
    val type: SchemaType.StringInterpolatableType,
    override val trace: Trace,
    override val contexts: Contexts,
) : ScalarOrReference<TS> {
    init {
        require(parts.any { it is Part.Reference }) {
            "Makes no sense to construct StringInterpolationValue without references"
        }
    }

    override fun withContexts(contexts: Contexts) = copy(contexts = contexts)

    sealed interface Part {
        data class Reference(val referencePath: List<String>) : Part {
            init {
                require(referencePath.isNotEmpty()) { "`referencePath` can't be empty" }
            }
        }
        data class Text(val text: String): Part
    }
}

/**
 * This is a list node of a value tree that can hold indexed children.
 */
data class ListValue<TS : TreeState>(
    val children: List<TreeValue<TS>>,
    val type: SchemaType.ListType,
    override val trace: Trace,
    override val contexts: Contexts,
) : TreeValue<TS> {
    override fun withContexts(contexts: Contexts) = copy(contexts = contexts)

    inline fun <reified T : TreeValue<TS>> copy(
        trace: Trace = this.trace,
        contexts: Contexts = this.contexts,
        crossinline transform: (value: T) -> List<TreeValue<TS>>?,
    ) = ListValue(
        children = children.flatMap { if (it is T) transform(it).orEmpty() else listOf(it) },
        trace = trace,
        contexts = contexts,
        type = type,
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
interface MapLikeValue<out TS : TreeState> : TreeValue<TS> {
    val children: MapLikeChildren<TS>
    val type: SchemaType.MapLikeType
    override fun withContexts(contexts: Contexts) = copy(contexts = contexts)
    data class Property<out T : TreeValue<*>>(
        val key: String,
        val kTrace: Trace,
        val value: T,
        val pType: SchemaObjectDeclaration.Property?,
    ) : WithContexts {
        constructor(key: String, kTrace: Trace, value: T, parentType: SchemaObjectDeclaration) :
                this(key, kTrace, value, parentType.getProperty(key))
        
        constructor(value: T, kTrace: Trace, pType: SchemaObjectDeclaration.Property) :
                this(pType.name, kTrace, value, pType)
        
        // This constructor is needed to change the type of the value while copying.
        constructor(other: Property<*>, value: T) :
                this(other.key, other.kTrace, value, other.pType)

        override val contexts get() = value.contexts

        fun <R : TreeValue<*>> copy(newValue: R) = Property(key, kTrace, newValue, pType)
    }
}

val MapLikeValue<*>.declaration: SchemaObjectDeclaration? get() = when(val type = type) {
    is SchemaType.MapType -> null
    is SchemaType.ObjectType -> type.declaration
}
// Convenient aliases for typed map-like nodes.
typealias MapProperty<TS> = Property<MapLikeValue<TS>>
typealias ScalarProperty<TS> = Property<ScalarValue<TS>>
typealias MapLikeChildren<TS> = List<Property<TreeValue<TS>>>

// Convenient accessors for typed map-like nodes.
fun <TS : TreeState> MapLikeValue<TS>.get(key: String, type: SchemaObjectDeclaration.Property?) =
    children.filter { it.key == key && it.pType == type }
inline val <TS : TreeState> TreeValue<TS>.asMapLike get() = asSafely<MapLikeValue<TS>>()
inline val <TS : TreeState> List<Property<TreeValue<TS>>>.values get() = map { it.value }

// Convenient accessors for named properties.
operator fun <TS : TreeState> MapLikeValue<TS>.get(key: String) = children.filter { it.key == key }
operator fun <TS : TreeState> List<MapLikeValue<TS>>.get(key: String) = flatMap { it[key] }
operator fun <TS : TreeState> MapLikeValue<TS>.get(prop: KProperty1<*, *>) = this[prop.name]

operator fun TreeValue<Refined>?.get(property: KProperty1<*, *>) = this[property.name]
operator fun TreeValue<Refined>?.get(property: String) = (this as? Refined)?.refinedChildren[property]?.value