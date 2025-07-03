/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.meta.ATypesVisitor
import org.jetbrains.amper.frontend.meta.DefaultSchemaTypingContext
import org.jetbrains.amper.frontend.types.SchemaType
import kotlin.reflect.KClass
import kotlin.reflect.full.starProjectedType


interface NestedCompletionNode {
    val name: String
    val parent: NestedCompletionNode?
    val children: List<NestedCompletionNode>

    /**
     * If this node describes completion for a string with regular expression.
     * 
     * P.S. The most common case is for modifiers, like `settings@smth`.
     */
    val isRegExp: Boolean

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

    /**
     * We want regexp nodes to be isolated because further 
     * completion from them does not seem reasonable 
     * (if we started completion from a top level node).
     */
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
            .visitAType(DefaultSchemaTypingContext.getType(root.starProjectedType))
            .let { NestedCompletionSchemaBuilder(NestedCompletionNodeImpl("", it)) }
    }
}

internal object NestedCompletionBuilder : ATypesVisitor<List<NestedCompletionNodeImpl>> {
    fun modifiersRegExp(propName: String) = "$propName(@[A-z]+)?"
    private val empty = emptyList<NestedCompletionNodeImpl>()
    override fun visitEnum(type: SchemaType.EnumType) = empty
    override fun visitScalar(type: SchemaType.ScalarType) = empty
    override fun visitPolymorphic(type: SchemaType.VariantType) = empty
    override fun visitList(type: SchemaType.ListType) = empty
    override fun visitMap(type: SchemaType.MapType) = empty
    override fun visitObject(type: SchemaType.ObjectType) =
        type.declaration.properties.flatMap { prop ->
            if (prop.type !is SchemaType.ObjectType) return@flatMap listOf()
            val possibleNames = buildList {
                add(prop.name to false)
                // Handle modifier-aware properties.
                if (prop.isModifierAware) {
                    add(modifiersRegExp(prop.name) to true)
                    // We add `test-` explicitly because in YAML that is a form of `@test` modifier.
                    add("test-${prop.name}" to false)
                    add(modifiersRegExp("test-${prop.name}") to true)
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