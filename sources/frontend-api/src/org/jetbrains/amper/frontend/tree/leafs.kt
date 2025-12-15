/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.types.SchemaType

/**
 * Represents an invalid/missing value that the parser was unable to parse.
 */
class ErrorNode(
    /**
     * If there is no PSI element at all for the (absent) value, this trace points to the key-value element that
     * contains no value (E.g. `foo: ` in YAML).
     */
    override val trace: Trace,
) : LeafTreeNode {
    override val contexts = EmptyContexts
}

/**
 * Represents the null literal.
 */
class NullLiteralNode(
    override val trace: Trace,
    override val contexts: Contexts,
) : LeafTreeNode

/**
 * This is a scalar value tree node.
 * See the sealed inheritors for type-safe access to the actual value.
 */
sealed interface ScalarNode : LeafTreeNode {
    val type: SchemaType.ScalarType
}
/**
 * This is a reference value tree node, pointing to some subtree.
 */
class ReferenceNode(
    val referencedPath: List<String>,
    val type: SchemaType,
    override val trace: Trace,
    override val contexts: Contexts,
) : LeafTreeNode {
    init {
        require(referencedPath.isNotEmpty()) { "`referencePath` can't be empty" }
    }
}

/**
 * String interpolation node, containing one or more [references][Part.Reference] inside a string.
 */
class StringInterpolationNode(
    val parts: List<Part>,
    val type: SchemaType.StringInterpolatableType,
    override val trace: Trace,
    override val contexts: Contexts,
) : LeafTreeNode {
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
