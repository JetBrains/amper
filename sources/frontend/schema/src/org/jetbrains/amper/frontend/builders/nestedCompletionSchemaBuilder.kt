/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.meta.ATypesDiscoverer
import org.jetbrains.amper.frontend.meta.ATypesVisitor
import org.jetbrains.amper.frontend.types.ATypes
import kotlin.reflect.KClass
import kotlin.reflect.full.starProjectedType


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

internal class NestedCompletionNodeImpl(
    override val name: String,
    override val children: List<NestedCompletionNodeImpl>,
    override var parent: NestedCompletionNodeImpl?,
    override val isRegExp: Boolean = false,
) : NestedCompletionNode {
    override val isolated = isRegExp
    
    /**
     * Constructor that sets up parent for passed children.
     */
    constructor(name: String, children: List<NestedCompletionNodeImpl>, isRegExp: Boolean = false) :
            this(name, children, null, isRegExp) {
        children.forEach { it.parent = this }
    }
    
    override fun equals(other: Any?) = this === other || (other is NestedCompletionNode && name == other.name)
    override fun hashCode() = name.hashCode()
}

/**
 * A visitor that traverses the tree and put all nested elements schema
 * info into a tree structure defined with [NestedCompletionNode].
 */
// TODO Simplify this IDE related API.
@UsedInIdePlugin
class NestedCompletionSchemaBuilder internal constructor(
    @UsedInIdePlugin
    val currentNode: NestedCompletionNode
) {
    companion object {
        @UsedInIdePlugin
        fun buildNestedCompletionTree(root: KClass<*>) = NestedCompletionBuilder
            .visitAType(ATypesDiscoverer[root.starProjectedType])
            .let { NestedCompletionSchemaBuilder(NestedCompletionNodeImpl("", it)) }
    }
}

internal object NestedCompletionBuilder : ATypesVisitor<List<NestedCompletionNodeImpl>> {
    fun modifiersRegExp(propName: String) = "$propName(@[A-z]+)?"
    private val empty = emptyList<NestedCompletionNodeImpl>()
    override fun visitEnum(type: ATypes.AEnum) = empty
    override fun visitScalar(type: ATypes.AScalar) = empty
    override fun visitPolymorphic(type: ATypes.APolymorphic) = empty
    override fun visitList(type: ATypes.AList) = empty
    override fun visitMap(type: ATypes.AMap) = empty
    override fun visitObject(type: ATypes.AObject) =
        type.properties.flatMap { prop ->
            if (prop.type !is ATypes.AObject) return@flatMap listOf()
            val possibleNames = buildList {
                add(prop.meta.name to false)
                // Handle modifier-aware properties.
                if (prop.meta.modifierAware != null) {
                    add(modifiersRegExp(prop.meta.name) to true)
                    // We add `test-` explicitly because in YAML that is a form of `@test` modifier.
                    add("test-${prop.meta.name}" to false)
                    add(modifiersRegExp("test-${prop.meta.name}") to true)
                }
            }
            // Propagate isolated nodes to the top level completion nodes list
            val toTopLevel = prop.type.accept().filter { it.isRegExp }
            // Other nodes are added as children.
            toTopLevel + possibleNames.map { (name, isRegex) ->
                NestedCompletionNodeImpl(name, prop.type.accept().filterNot { it.isRegExp }, isRegex)
            }
        }
}