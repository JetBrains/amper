/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import kotlin.reflect.KProperty1

/**
 * This is a mapping node in a value tree that is holding either a [map][SchemaType.MapType] or
 * an [object][org.jetbrains.amper.frontend.types.SchemaTypeDeclaration], which is determined by [type] field.
 *
 * This node can have more than one [KeyValue] with different contexts but with the same [key][KeyValue.key].
 *
 * @see RefinedMappingNode
 */
// NOTE: `sealed` is needed here to appease the compiler's exhaustiveness checks.
// TODO: Maybe introduce map/object sub-interfaces for better expressiveness?
sealed interface MappingNode : TreeNode {
    val children: List<KeyValue>
    val type: SchemaType.MapLikeType
}

/**
 * Same as [MappingNode], but guarantess key string uniqueness in [children].
 * Also provides access to the [key-values][RefinedKeyValue] as a map - [refinedChildren].
 */
interface RefinedMappingNode : MappingNode, RefinedTreeNode {
    override val children : List<RefinedKeyValue>
    val refinedChildren : Map<String, RefinedKeyValue>
}

fun MappingNode(
    children: List<KeyValue>,
    type: SchemaType.MapLikeType,
    trace: Trace,
    contexts: Contexts,
): MappingNode = MappingNodeImpl(children, type, trace, contexts)

fun RefinedMappingNode(
    refinedChildren: Map<String, RefinedKeyValue>,
    type: SchemaType.MapLikeType,
    trace: Trace,
    contexts: Contexts,
) : RefinedMappingNode = RefinedMappingNodeImpl(refinedChildren, type, trace, contexts)

/**
 * NOTE: Doesn't check given [children] for key uniqueness criteria.
 * Thus, the resulting copy will never be a [RefinedMappingNode], even if the original node was one.
 */
fun MappingNode.copy(
    children: List<KeyValue> = this.children,
    type: SchemaType.MapLikeType = this.type,
    trace: Trace = this.trace,
    contexts: Contexts = this.contexts,
): MappingNode = MappingNode(children, type, trace, contexts)

fun RefinedMappingNode.copy(
    refinedChildren: Map<String, RefinedKeyValue> = this.refinedChildren,
    type: SchemaType.MapLikeType = this.type,
    trace: Trace = this.trace,
    contexts: Contexts = this.contexts,
): RefinedMappingNode = RefinedMappingNode(refinedChildren, type, trace, contexts)

/**
 * Returns the value from the mapping with the key equal to the name of the given [property],
 * if this is a [RefinedMappingNode] and it has such a value;
 * `null` otherwise.
 */
operator fun RefinedTreeNode?.get(property: KProperty1<out SchemaNode, *>): RefinedTreeNode? = get(property.name)

/**
 * Returns the value from the mapping with the key [property],
 * if this is a [RefinedMappingNode] and it has such a value;
 * `null` otherwise.
 */
operator fun RefinedTreeNode?.get(property: String): RefinedTreeNode? =
    (this as? RefinedMappingNode)?.refinedChildren[property]?.value

/**
 * Returns type declaration, if an object. `null` if a map.
 */
val MappingNode.declaration: SchemaObjectDeclaration? get() = when(val type = type) {
    is SchemaType.MapType -> null
    is SchemaType.ObjectType -> type.declaration
}

private class MappingNodeImpl(
    override val children: List<KeyValue>,
    override val type: SchemaType.MapLikeType,
    override val trace: Trace,
    override val contexts: Contexts,
) : MappingNode

private class RefinedMappingNodeImpl(
    override val refinedChildren: Map<String, RefinedKeyValue>,
    override val type: SchemaType.MapLikeType,
    override val trace: Trace,
    override val contexts: Contexts,
) : RefinedMappingNode {
    override val children = refinedChildren.values.toList()
}
