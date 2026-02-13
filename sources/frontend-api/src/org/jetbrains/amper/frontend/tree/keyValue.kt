/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.contexts.WithContexts
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType

/**
 * A key-value pair in [MappingNode.children].
 */
interface KeyValue : WithContexts, Traceable {
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
 * A key-value pair in [CompleteMapNode].
 *
 * [propertyDeclaration] is always `null`.
 */
interface CompleteKeyValue : RefinedKeyValue {
    override val propertyDeclaration: Nothing?
    override val value: CompleteTreeNode
}

/**
 * A key-value pair in [CompleteObjectNode].
 *
 * [propertyDeclaration] is always present.
 */
interface CompletePropertyKeyValue : RefinedKeyValue {
    override val propertyDeclaration: SchemaObjectDeclaration.Property
    override val value: CompleteTreeNode
}

/**
 * Creates a [org.jetbrains.amper.frontend.tree.KeyValue] instance, fetching the property from the [parentType].
 */
fun KeyValue(
    key: String,
    keyTrace: Trace,
    value: TreeNode,
    parentType: SchemaObjectDeclaration,
    trace: Trace,
): KeyValue = KeyValueImpl(
    key, keyTrace, value,
    requireNotNull(parentType.getProperty(key)) { "No property $key found in $parentType" },
    trace,
)

/**
 * Creates a [org.jetbrains.amper.frontend.tree.KeyValue] instance for a map.
 */
fun KeyValue(
    key: String,
    keyTrace: Trace,
    value: TreeNode,
    trace: Trace,
) : KeyValue = KeyValueImpl(key, keyTrace, value, null, trace)

/**
 * Creates a [org.jetbrains.amper.frontend.tree.KeyValue] instance using the supplied [propertyDeclaration].
 */
fun KeyValue(
    keyTrace: Trace,
    value: TreeNode,
    propertyDeclaration: SchemaObjectDeclaration.Property,
    trace: Trace,
) : KeyValue = KeyValueImpl(propertyDeclaration.name, keyTrace, value, propertyDeclaration, trace)

/**
 * Creates a [org.jetbrains.amper.frontend.tree.RefinedKeyValue] instance using the supplied [propertyDeclaration].
 */
fun RefinedKeyValue(
    keyTrace: Trace,
    value: RefinedTreeNode,
    propertyDeclaration: SchemaObjectDeclaration.Property,
    trace: Trace,
): RefinedKeyValue = RefinedKeyValueImpl(propertyDeclaration.name, keyTrace, value, propertyDeclaration, trace)

/**
 * Copies the key-value node as an *unrefined* node, replacing its value to the supplied [value].
 */
fun KeyValue.copyWithValue(
    value: TreeNode,
): KeyValue = KeyValueImpl(key, keyTrace, value, propertyDeclaration, trace)

/**
 * Copies the key-value node as a *refined* node, replacing its value to the supplied [value].
 */
fun KeyValue.copyWithValue(
    value: RefinedTreeNode,
) : RefinedKeyValue = RefinedKeyValueImpl(key, keyTrace, value, propertyDeclaration, trace)

/**
 * Copies the key-value as a *complete* one designated for [CompleteMapNode], using the supplied [value].
 */
fun KeyValue.asCompleteForMap(
    value: CompleteTreeNode,
) : CompleteKeyValue = CompleteKeyValueImpl(key, keyTrace, value, trace).also {
    require(propertyDeclaration == null) { "`propertyDeclaration` is not null" }
}

/**
 * Copies the key-value as a *complete* one designated for [CompleteObjectNode], using the supplied [value].
 */
fun KeyValue.asCompleteForObject(
    value: CompleteTreeNode,
): CompletePropertyKeyValue = CompletePropertyKeyValueImpl(key, keyTrace, value, checkNotNull(propertyDeclaration) {
    "`propertyDeclaration` is null"
}, trace)

private data class KeyValueImpl(
    override val key: String,
    override val keyTrace: Trace,
    override val value: TreeNode,
    override val propertyDeclaration: SchemaObjectDeclaration.Property?,
    override val trace: Trace,
) : KeyValue, WithContexts by value

private data class RefinedKeyValueImpl(
    override val key: String,
    override val keyTrace: Trace,
    override val value: RefinedTreeNode,
    override val propertyDeclaration: SchemaObjectDeclaration.Property?,
    override val trace: Trace,
) : RefinedKeyValue, WithContexts by value

private data class CompleteKeyValueImpl(
    override val key: String,
    override val keyTrace: Trace,
    override val value: CompleteTreeNode,
    override val trace: Trace,
) : CompleteKeyValue, WithContexts by value {
    override val propertyDeclaration: Nothing? get() = null
}

private data class CompletePropertyKeyValueImpl(
    override val key: String,
    override val keyTrace: Trace,
    override val value: CompleteTreeNode,
    override val propertyDeclaration: SchemaObjectDeclaration.Property,
    override val trace: Trace,
) : CompletePropertyKeyValue, WithContexts by value