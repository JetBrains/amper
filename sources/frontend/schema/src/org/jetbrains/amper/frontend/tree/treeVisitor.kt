/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

private fun <R> TreeVisitor<R>.accept(node: TreeNode): R = when (node) {
    is ListNode -> visitList(node)
    is MappingNode -> visitMap(node)
    is ScalarNode -> visitScalar(node)
    is NullLiteralNode -> visitNull(node)
    is ReferenceNode -> visitReference(node)
    is StringInterpolationNode -> visitStringInterpolation(node)
    is ErrorNode -> visitError(node)
}

private fun <R> RefinedTreeVisitor<R>.accept(node: RefinedTreeNode): R = when (node) {
    is RefinedListNode -> visitList(node)
    is RefinedMappingNode -> visitMap(node)
    is ScalarNode -> visitScalar(node)
    is NullLiteralNode -> visitNull(node)
    is ReferenceNode -> visitReference(node)
    is StringInterpolationNode -> visitStringInterpolation(node)
    is ErrorNode -> visitError(node)
}

private fun <R> CompleteTreeVisitor<R>.accept(node: CompleteTreeNode): R = when (node) {
    is CompleteListNode -> visitList(node)
    is CompleteMapNode -> visitMap(node)
    is CompleteObjectNode -> visitObject(node)
    is ScalarNode -> visitScalar(node)
    is NullLiteralNode -> visitNull(node)
}

/**
 * A generic [TreeNode] visitor capable of returning a result of type [R].
 */
interface TreeVisitor<R> {
    fun visit(node: TreeNode): R = accept(node)
    fun visitNull(node: NullLiteralNode): R
    fun visitScalar(node: ScalarNode): R
    fun visitError(node: ErrorNode): R
    fun visitReference(node: ReferenceNode): R
    fun visitStringInterpolation(node: StringInterpolationNode): R
    fun visitList(node: ListNode): R
    fun visitMap(node: MappingNode): R
}

/**
 * A generic [RefinedTreeNode] visitor capable of returning a result of type [R].
 */
interface RefinedTreeVisitor<R> {
    fun visit(node: RefinedTreeNode): R = accept(node)
    fun visitNull(node: NullLiteralNode): R
    fun visitScalar(node: ScalarNode): R
    fun visitError(node: ErrorNode): R
    fun visitReference(node: ReferenceNode): R
    fun visitStringInterpolation(node: StringInterpolationNode): R
    fun visitList(node: RefinedListNode): R
    fun visitMap(node: RefinedMappingNode): R
}

interface CompleteTreeVisitor<R> {
    fun visit(node: CompleteTreeNode): R = accept(node)
    fun visitNull(node: NullLiteralNode): R
    fun visitScalar(node: ScalarNode): R
    fun visitList(node: CompleteListNode): R
    fun visitMap(node: CompleteMapNode): R
    fun visitObject(node: CompleteObjectNode): R
}

/**
 * A recursive implementation of [TreeVisitor] that aggregates results of each node's children (if any).
 */
abstract class RecurringTreeVisitor<R> : TreeVisitor<R> {
    abstract fun aggregate(node: TreeNode, childResults: List<R>): R
    open fun aggregateList(node: ListNode, childResults: List<R>): R = aggregate(node, childResults)
    open fun aggregateMap(node: MappingNode, childResults: List<R>): R = aggregate(node, childResults)

    override fun visitList(node: ListNode): R =
        aggregateList(node, node.children.map(::visit))

    override fun visitMap(node: MappingNode): R =
        aggregateMap(node, node.children.map { it.value }.map(::visit))
}

/**
 * Convenient specification of [TreeVisitor] that returns nothing.
 */
abstract class RecurringTreeVisitorUnit : RecurringTreeVisitor<Unit>() {
    override fun aggregate(node: TreeNode, childResults: List<Unit>) = Unit
    override fun visitScalar(node: ScalarNode) = Unit
    override fun visitNull(node: NullLiteralNode) = Unit
    override fun visitReference(node: ReferenceNode) = Unit
    override fun visitStringInterpolation(node: StringInterpolationNode) = Unit
    override fun visitError(node: ErrorNode) = Unit
}

/**
 * Do visit every value of the tree.
 */
fun TreeNode.visitNodes(block: (TreeNode) -> Unit) =
    object : RecurringTreeVisitorUnit() {
        override fun visitNull(node: NullLiteralNode) = block(node)
        override fun visitScalar(node: ScalarNode) = block(node)
        override fun visitReference(node: ReferenceNode) = block(node)
        override fun visitList(node: ListNode) = block(node).also { super.visitList(node) }
        override fun visitMap(node: MappingNode) = block(node).also { super.visitMap(node) }
    }.visit(this)

/**
 * Do visit every map like value of the tree.
 */
fun TreeNode.visitMappingNodes(block: (MappingNode) -> Unit) =
    object : RecurringTreeVisitorUnit() {
        override fun visitMap(node: MappingNode) = block(node).also { super.visitMap(node) }
    }.visit(this)

sealed interface TransformResult<out T>
class Changed<T>(val value: T) : TransformResult<T>
object NotChanged : TransformResult<Nothing>
object Removed : TransformResult<Nothing>

private fun <T, R> TransformResult<T>.mapIfChanged(block: (T) -> R): TransformResult<R> =
    when (this@mapIfChanged) {
        is Changed<T> -> Changed(block(value))
        is NotChanged -> this
        is Removed -> this
    }

/**
 * [TreeVisitor] implementation that is copying the tree on each node if a replacement is provided by
 * a respective `visit*` method.
 *
 * Each visit method returns `null` if nothing had changed, or transformed tree value.
 *
 * To remove a property from `MapLikeValue` or an element from `ListValue` one should
 * override `visitMapValue` or `visitListValue` respectively or return [ErrorNode].
 * TODO: refactor this back to eager copying, because "changes" tracking is unproven to enhance performance.
 */
abstract class TreeTransformer : TreeVisitor<TransformResult<TreeNode>> {
    /**
     * Return `null` if value was removed or intact/changed value instead.
     */
    fun transform(node: TreeNode): TreeNode? =
        when (val transformed = visit(node)) {
            is Changed -> transformed.value
            NotChanged -> node
            Removed -> null
        }

    override fun visitError(node: ErrorNode): TransformResult<TreeNode> = NotChanged
    override fun visitNull(node: NullLiteralNode): TransformResult<TreeNode> = NotChanged
    override fun visitScalar(node: ScalarNode): TransformResult<TreeNode> = NotChanged
    override fun visitReference(node: ReferenceNode): TransformResult<TreeNode> = NotChanged
    override fun visitStringInterpolation(node: StringInterpolationNode): TransformResult<TreeNode> = NotChanged

    override fun visitList(node: ListNode): TransformResult<TreeNode> =
        node.children.visitAll().mapIfChanged { node.copy(children = it) }

    override fun visitMap(node: MappingNode) =
        node.children.visitAll().mapIfChanged { node.copy(children = it) }

    @JvmName("acceptAllList")
    fun List<TreeNode>.visitAll(): TransformResult<List<TreeNode>> {
        val childrenTransformed = map { visit(it) }
        return if (childrenTransformed.all { it === NotChanged }) NotChanged
        else childrenTransformed.mapIndexedNotNull { i, it ->
            when (it) {
                is Changed -> it.value
                NotChanged -> this[i]
                Removed -> null
            }
        }.let(::Changed)
    }

    @JvmName("acceptAllMapLikeChildren")
    fun List<KeyValue>.visitAll(): TransformResult<List<KeyValue>> {
        val childrenTransformed = map { visit(it.value) }
        return if (childrenTransformed.all { it === NotChanged }) NotChanged
        else childrenTransformed.mapIndexedNotNull { i, it ->
            when (it) {
                is Changed -> this[i].copyWithValue(value = it.value)
                NotChanged -> this[i]
                Removed -> null
            }
        }.let(::Changed)
    }
}

/**
 * [RefinedTreeVisitor] implementation that is copying the tree on each node if a replacement is provided by
 * a respective `visit*` method.
 */
abstract class RefinedTreeTransformer : RefinedTreeVisitor<RefinedTreeNode?> {
    override fun visitError(node: ErrorNode): RefinedTreeNode? = node
    override fun visitNull(node: NullLiteralNode): RefinedTreeNode? = node
    override fun visitScalar(node: ScalarNode): RefinedTreeNode? = node
    override fun visitReference(node: ReferenceNode): RefinedTreeNode? = node
    override fun visitStringInterpolation(node: StringInterpolationNode): RefinedTreeNode? = node

    override fun visitList(node: RefinedListNode): RefinedTreeNode =
        node.copy(children = node.children.mapNotNull { visit(it) })

    override fun visitMap(node: RefinedMappingNode) =
        node.copy(refinedChildren = buildMap {
            for ((key, property) in node.refinedChildren) {
                visit(property.value)?.let { put(key, property.copyWithValue(value = it)) }
            }
        })
}