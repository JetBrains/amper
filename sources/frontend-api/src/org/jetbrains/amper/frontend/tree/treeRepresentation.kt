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
sealed interface TreeValue<out TS : TreeState> : WithContexts, Traceable

/**
 * A value that doesn't have any children.
 */
sealed interface LeafTreeValue : TreeValue<Nothing>

/**
 * Represents an invalid value that was unable to be parsed.
 */
class ErrorValue(
    /**
     * If there is no PSI element at all for the (absent) value, this trace points to the key-value element that
     * contains no value (E.g. `foo: ` in YAML).
     */
    override val trace: Trace,
) : LeafTreeValue {
    override val contexts = EmptyContexts
}

/**
 * Represents the null literal.
 */
data class NullValue(
    override val trace: Trace,
    override val contexts: Contexts,
) : LeafTreeValue

/**
 * This is a scalar value tree node. E.g., string, boolean, enum or path.
 */
data class ScalarValue(
    val value: Any,
    val type: SchemaType.ScalarType,
    override val trace: Trace,
    override val contexts: Contexts,
) : LeafTreeValue

/**
 * This is a reference value tree node, pointing to some subtree.
 */
data class ReferenceValue(
    val referencedPath: List<String>,
    val type: SchemaType,
    override val trace: Trace,
    override val contexts: Contexts,
) : LeafTreeValue {
    init {
        require(referencedPath.isNotEmpty()) { "`referencePath` can't be empty" }
    }
}

data class StringInterpolationValue(
    val parts: List<Part>,
    val type: SchemaType.StringInterpolatableType,
    override val trace: Trace,
    override val contexts: Contexts,
) : LeafTreeValue {
    init {
        require(parts.any { it is Part.Reference }) {
            "Makes no sense to construct StringInterpolationValue without references"
        }
    }

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
) : TreeValue<TS>

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

typealias MapLikeChildren<TS> = List<Property<TreeValue<TS>>>

inline val <TS : TreeState> TreeValue<TS>.asMapLike get() = this as? MapLikeValue<TS>
inline val TreeValue<*>.asScalar get() = asSafely<ScalarValue>()

inline fun <reified T : Any> TreeValue<*>.scalarValue() = asScalar?.value as? T

operator fun TreeValue<Refined>?.get(property: KProperty1<*, *>) = this[property.name]
operator fun TreeValue<Refined>?.get(property: String) = (this as? Refined)?.refinedChildren[property]?.value