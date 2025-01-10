/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.frontend.api.Default
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType

interface NestedCompletionNode {
    val name: String
    val isRegExp: Boolean
    val parent: NestedCompletionNode?
    val children: List<NestedCompletionNode>

    /**
     * Isolated tree node doesn't forward nested completions from outside down into it's children
     * (completion is available starting from the level of the node, not from outside)
     */
    val isolated: Boolean
}

fun NestedCompletionNode.iterateThrough(level: Int = 0, block: (NestedCompletionNode, Int) -> Unit) {
    val node = this
    node.children.forEach {
        block(it, level)
        it.iterateThrough(level + 2, block)
    }
}

internal data class NestedCompletionNodeImpl (
    override val name: String,
    override val isRegExp: Boolean = false,
    override val parent: NestedCompletionNodeImpl?,
    override val children: MutableList<NestedCompletionNodeImpl> = mutableListOf(),
    override val isolated: Boolean = false
) : NestedCompletionNode {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NestedCompletionNode) return false
        if (name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

/**
 * A visitor, that traverses the tree and put all nested elements schema info into tree structure defined with NestedCompletionNode.
 */
class NestedCompletionSchemaBuilder(
    var currentNode: NestedCompletionNode
) : RecurringVisitor() {
    companion object {
        fun buildNestedCompletionTree(
            root: KClass<*>
        ) = NestedCompletionSchemaBuilder(NestedCompletionNodeImpl("", parent = null))
            .apply {
                visitClas(root)
            }

        fun modifiersRegExp(propName: String) = "$propName(@[A-z]+)?"
    }

    override fun visitClas(klass: KClass<*>) =
        visitSchema(klass, NestedCompletionSchemaBuilder(currentNode))


    override fun visitTyped(
        prop: KProperty<*>,
        type: KType,
        schemaNodeType: KType,
        types: Collection<KClass<*>>,
        modifierAware: Boolean
    ) {
        val previousNode = currentNode as NestedCompletionNodeImpl
        when {
            type.isSchemaNode -> {
                val nestedNode = NestedCompletionNodeImpl(prop.name, parent = previousNode)
                previousNode.children.add(nestedNode)
                currentNode = nestedNode
                types.forEach { visitClas(it) }
                currentNode = previousNode
            }
            type.isMap && type.mapValueType.isSchemaNode && modifierAware -> {
                // first visit the node with the exact name
                val nestedPlainNode = NestedCompletionNodeImpl(prop.name, isRegExp = false, parent = previousNode)
                previousNode.children.add(nestedPlainNode)
                currentNode = nestedPlainNode
                types.forEach { visitClas(it) }
                // then create a node for regex matches for platform-specific nodes
                val parent = previousNode.firstParent() // isolated nodes are bound to the top level completion nodes list
                val nestedNode = NestedCompletionNodeImpl(modifiersRegExp(prop.name), isRegExp = true, parent = parent, isolated = true)
                parent.children.add(nestedNode)
                currentNode = nestedNode
                types.forEach { visitClas(it) }
                currentNode = previousNode
            }

            type.isMap -> {} // do nothing - skip

            type.isCollection -> {} // do nothing - skip

            else -> error("Unsupported type $type")
        }
    }

    override fun visitCommon(prop: KProperty<*>, type: KType, default: Default<*>?) {
        // do nothing - skip
    }

    private fun NestedCompletionNodeImpl.firstParent(): NestedCompletionNodeImpl = this.parent?.firstParent() ?: this
}