/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics.helpers

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.tree.ErrorNode
import org.jetbrains.amper.frontend.tree.ListNode
import org.jetbrains.amper.frontend.tree.KeyValue
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.NullLiteralNode
import org.jetbrains.amper.frontend.tree.RecurringTreeVisitor
import org.jetbrains.amper.frontend.tree.RecurringTreeVisitorUnit
import org.jetbrains.amper.frontend.tree.ReferenceNode
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.StringInterpolationNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.declaration
import org.jetbrains.amper.frontend.types.isSameAs
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Visit all passed scalar properties within given [TreeNode].
 */
inline fun <reified T : SchemaNode, reified V> TreeNode.visitScalarProperties(
    vararg properties: KProperty1<T, V>,
    noinline visitSelected: (KeyValue, V & Any) -> Unit,
) {
    ObjectPropertiesVisitorRecurring(
        objectKlass = T::class,
        properties = properties.map { it.name },
    ) {
        val node = it.value as? ScalarNode ?: return@ObjectPropertiesVisitorRecurring
        val value = node.value
        if (value is V) visitSelected(it, value)
    }.visit(this)
}

/**
 * Visit all passed [MappingNode] properties within given [TreeNode].
 */
inline fun <reified T : SchemaNode> TreeNode.visitListProperties(
    vararg properties: KProperty1<T, *>,
    noinline visitSelected: (KeyValue, ListNode) -> Unit,
) = ObjectPropertiesVisitorRecurring(
    objectKlass = T::class,
    properties = properties.map { it.name },
) {
    val node = it.value as? ListNode ?: return@ObjectPropertiesVisitorRecurring
    visitSelected(it, node)
}.visit(this)

/**
 * Visits properties with keys from [properties] in objects with [objectKlass] type.
 */
class ObjectPropertiesVisitorRecurring(
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