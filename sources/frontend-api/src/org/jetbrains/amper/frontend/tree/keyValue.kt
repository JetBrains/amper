/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.WithContexts
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType

/**
 * A key-value pair in [MappingNode.children].
 */
interface KeyValue : WithContexts {
    /**
     * Key string.
     */
    val key: String

    /**
     * Trace for the key string.
     * TODO: Use TraceableString for key instead?
     */
    val keyTrace: Trace

    /**
     * Value node.
     */
    val value: TreeNode

    /**
     * Property declaration, if the mapping this key-value belongs to is an [object][SchemaObjectDeclaration].
     * `null` if this belongs to a [map][SchemaType.MapType].
     */
    val propertyDeclaration: SchemaObjectDeclaration.Property?
}

/**
 * A key-value pair in [RefinedMappingNode.children].
 */
interface RefinedKeyValue : KeyValue {
    override val value: RefinedTreeNode
}

/**
 * Creates a [org.jetbrains.amper.frontend.tree.KeyValue] instance, fetching the property from the [parentType].
 */
fun KeyValue(
    key: String,
    keyTrace: Trace,
    value: TreeNode,
    parentType: SchemaObjectDeclaration,
): KeyValue = KeyValueImpl(
    key, keyTrace, value,
    requireNotNull(parentType.getProperty(key)) { "No property $key found in $parentType" },
)

/**
 * Creates a [org.jetbrains.amper.frontend.tree.KeyValue] instance for a map.
 */
fun KeyValue(
    key: String,
    keyTrace: Trace,
    value: TreeNode,
) : KeyValue = KeyValueImpl(key, keyTrace, value, null)

/**
 * Creates a [org.jetbrains.amper.frontend.tree.KeyValue] instance using the supplied [propertyDeclaration].
 */
fun KeyValue(
    keyTrace: Trace,
    value: TreeNode,
    propertyDeclaration: SchemaObjectDeclaration.Property,
) : KeyValue = KeyValueImpl(propertyDeclaration.name, keyTrace, value, propertyDeclaration)

/**
 * Copies the key-value node as an *unrefined* node, replacing its value to the supplied [value].
 */
fun KeyValue.copyWithValue(
    value: TreeNode,
): KeyValue = KeyValueImpl(key, keyTrace, value, propertyDeclaration)

/**
 * Copies the key-value node as a *refined* node, replacing its value to the supplied [value].
 */
fun KeyValue.copyWithValue(
    value: RefinedTreeNode,
) : RefinedKeyValue = RefinedKeyValueImpl(key, keyTrace, value, propertyDeclaration)

private data class KeyValueImpl(
    override val key: String,
    override val keyTrace: Trace,
    override val value: TreeNode,
    override val propertyDeclaration: SchemaObjectDeclaration.Property?,
) : KeyValue, WithContexts by value

private data class RefinedKeyValueImpl(
    override val key: String,
    override val keyTrace: Trace,
    override val value: RefinedTreeNode,
    override val propertyDeclaration: SchemaObjectDeclaration.Property?,
) : RefinedKeyValue, WithContexts by value