/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics.helpers

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.ErrorNode
import org.jetbrains.amper.frontend.tree.KeyValue
import org.jetbrains.amper.frontend.tree.ListNode
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.NullLiteralNode
import org.jetbrains.amper.frontend.tree.RecurringTreeVisitor
import org.jetbrains.amper.frontend.tree.RecurringTreeVisitorUnit
import org.jetbrains.amper.frontend.tree.ReferenceNode
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.StringInterpolationNode
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.declaration
import org.jetbrains.amper.frontend.tree.enumConstantIfAvailable
import org.jetbrains.amper.frontend.types.isSameAs
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Visits the given [properties] in all instances of [T] within the given [TreeNode]'s descendants.
 * Properties with null values are skipped.
 */
inline fun <reified T : SchemaNode, reified V : Enum<*>?> TreeNode.visitEnumProperties(
    vararg properties: KProperty1<T, V>,
    noinline visitSelected: (KeyValue, V & Any) -> Unit,
) = visitProperties<T, EnumNode>(*properties) { keyValue, enumNode ->
    enumNode.enumConstantIfAvailable?.let { enum ->
        visitSelected(keyValue, (enum as V)!!)
    }
}

/**
 * Visits the given [properties] in all instances of [T] within the given [TreeNode]'s descendants.
 */
inline fun <reified T : SchemaNode> TreeNode.visitStringProperties(
    vararg properties: KProperty1<T, String>,
    noinline visitSelected: (KeyValue, String) -> Unit,
) = visitProperties<T, StringNode>(*properties) { keyValue, stringNode ->
    visitSelected(keyValue, stringNode.value)
}

/**
 * Visits the given [properties] in all instances of [T] within the given [TreeNode]'s descendants.
 */
inline fun <reified T : SchemaNode> TreeNode.visitNullableStringProperties(
    vararg properties: KProperty1<T, String?>,
    noinline visitSelected: (KeyValue, String?) -> Unit,
) = visitProperties(T::class, *properties) { keyValue ->
    val value = when (val valueNode = keyValue.value) {
        is StringNode -> valueNode.value
        is NullLiteralNode -> null
        else -> return@visitProperties
    }
    visitSelected(keyValue, value)
}

/**
 * Visits the given [properties] in all instances of [T] within the given [TreeNode]'s descendants.
 * Properties with values that are not nodes of type [ListNode] are skipped.
 */
inline fun <reified T : SchemaNode> TreeNode.visitListProperties(
    vararg properties: KProperty1<T, *>,
    noinline visitSelected: (KeyValue, ListNode) -> Unit,
) = visitProperties<T, ListNode>(*properties, visitSelected = visitSelected)

/**
 * Visits the given [properties] in all instances of [T] within the given [TreeNode]'s descendants.
 * Properties with values that are not nodes of type [VN] are skipped.
 */
@PublishedApi
internal inline fun <reified T : SchemaNode, reified VN : TreeNode> TreeNode.visitProperties(
    vararg properties: KProperty1<T, *>,
    noinline visitSelected: (KeyValue, VN) -> Unit,
) = visitProperties(T::class, *properties) { keyValue ->
    val node = keyValue.value as? VN ?: return@visitProperties
    visitSelected(keyValue, node)
}

/**
 * Visits the given [properties] in all instances of [T] within the given [TreeNode]'s descendants.
 */
@PublishedApi
internal fun <T : SchemaNode> TreeNode.visitProperties(
    objectType: KClass<T>,
    vararg properties: KProperty1<T, *>,
    visitSelected: (KeyValue) -> Unit,
) = ObjectPropertiesVisitorRecurring(
    objectKlass = objectType,
    properties = properties.map { it.name },
    visitSelected = visitSelected,
).visit(this)

/**
 * Visits properties with keys from [properties] in objects with [objectKlass] type.
 */
private class ObjectPropertiesVisitorRecurring(
    private val objectKlass: KClass<out SchemaNode>,
    private val properties: Collection<String>,
    private val visitSelected: (KeyValue) -> Unit,
) : RecurringTreeVisitorUnit() {

    override fun visitMap(node: MappingNode) {
        if (node.declaration?.isSameAs(objectKlass) == true) node.children.forEach { it.doVisitMapProperty() }
        else super.visitMap(node)
    }

    fun KeyValue.doVisitMapProperty() =
        if (key in properties) visitSelected(this)
        else visit(value)
}

/**
 * Visit all scalar properties within passed [TreeNode].
 * FIXME Need also check non scalars, but not objects.
 */
fun TreeNode.collectScalarPropertiesWithOwners() = AllScalarPropertiesCollector.visit(this)

private typealias PropertyWithOwner = Pair<MappingNode, KeyValue>
private typealias PropertiesWithOwner = List<PropertyWithOwner>

private object AllScalarPropertiesCollector : RecurringTreeVisitor<PropertiesWithOwner>() {
    override fun visitNull(node: NullLiteralNode) = emptyList<Nothing>()
    override fun visitScalar(node: ScalarNode) = emptyList<Nothing>()
    override fun visitError(node: ErrorNode) = emptyList<Nothing>()
    override fun visitReference(node: ReferenceNode) = emptyList<Nothing>()
    override fun visitStringInterpolation(node: StringInterpolationNode) = emptyList<Nothing>()
    override fun aggregate(node: TreeNode, childResults: List<PropertiesWithOwner>) = childResults.flatten()
    override fun visitMap(node: MappingNode) = super.visitMap(node) +
            node.children.filter { it.value is ScalarNode }.map { node to it }
}