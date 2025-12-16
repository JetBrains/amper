/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.jetbrains.amper.frontend.contexts.DefaultContext
import org.jetbrains.amper.frontend.contexts.TestCtx
import org.jetbrains.amper.frontend.schema.BomDependency
import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.IntNode
import org.jetbrains.amper.frontend.tree.ListNode
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.copy
import org.jetbrains.amper.frontend.tree.declaration
import org.jetbrains.amper.frontend.tree.schemaValue
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.NoopProblemReporter
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString

typealias Key = String

val treeRefiner = TreeRefiner()

fun MappingNode.serializeToYaml(): String = buildString {
    val refinedMain = context(NoopProblemReporter) {
        treeRefiner.refineTree(this@serializeToYaml, listOf())
    }
    val main = refinedMain.filterByContext(DefaultContext.ReactivelySet)
    val refinedTest = context(NoopProblemReporter) {
        treeRefiner.refineTree(this@serializeToYaml, listOf(TestCtx))
    }
    val test = refinedTest.filterByContext(TestCtx, main)

    append(main?.serializeToYaml() ?: "")
    if (test != null) {
        appendLine()
        append(test.serializeToYaml())
    }
}

fun TreeNode.serializeToYaml(indent: Int = 0): String = buildString {
    when (this@serializeToYaml) {
        is ListNode -> append(this@serializeToYaml.serializeToYaml(indent))
        is MappingNode -> append(this@serializeToYaml.serializeToYaml(indent))
        is ScalarNode -> append(this@serializeToYaml.serializeToYaml())
        else -> {}
    }
}

fun MappingNode.serializeToYaml(indent: Int = 0): String = buildString {
    val isFromKeyAndTheRestNestedProperties = children.filter { it.propertyDeclaration?.isFromKeyAndTheRestNested == true }
    val isCollapsibleFromKey = isFromKeyAndTheRestNestedProperties.size == 1

    if (isCollapsibleFromKey) {
        val collapsibleProperty = isFromKeyAndTheRestNestedProperties.single()
        val collapsiblePropertyValue = collapsibleProperty.value

        require(collapsiblePropertyValue is ScalarNode) {
            "Only scalar values can be collapsible"
        }

        val theRest = children.filter { it !== collapsibleProperty }
        append(" ")
        if (declaration?.createInstance() is BomDependency) {
            append("bom: ")
        }
        when (collapsiblePropertyValue) {
            is BooleanNode -> append(collapsiblePropertyValue.value)
            is EnumNode -> append(collapsiblePropertyValue.schemaValue)
            is IntNode -> append(collapsiblePropertyValue.value)
            is PathNode -> append(collapsiblePropertyValue.value.invariantSeparatorsPathString)
            is StringNode -> append(collapsiblePropertyValue.value)
        }

        if (theRest.isEmpty()) {
            appendLine()
            return@buildString
        } else {
            append(":")
            append(this@serializeToYaml.copy(theRest, type, trace, contexts).serializeToYaml(indent + 1))
            return@buildString
        }
    }

    for (child in children) {
        if (child.propertyDeclaration?.hasShorthand == true && children.size == 1) {
            when (child.propertyDeclaration?.type) {
                is SchemaType.BooleanType -> appendLine(" ${child.key}")
                else -> append(child.value.serializeToYaml(indent))
            }
        } else {
            if (indent > 0 && child == children.first()) {
                appendLine()
            }

            require(child.contexts.size == 1) {
                "After context selection there must be only one context"
            }

            val context = child.contexts.single()
            if (context is TestCtx) {
                if (indent == 0) {
                    append(("test-${child.key}").serializeToYaml(indent))
                } else {
                    append(child.key.serializeToYaml(indent))
                }
            } else {
                append(child.key.serializeToYaml(indent))
            }

            append(child.value.serializeToYaml(indent + 1))
            if (indent == 0 && child != children.last()) {
                appendLine()
            }
        }
    }
}

fun ListNode.serializeToYaml(indent: Int = 0): String = buildString {
    appendLine()
    for (item in children) {
        append("  ".repeat(indent))
        append("-")
        append(item.serializeToYaml(indent + 1))
    }
}

fun ScalarNode.serializeToYaml(): String = buildString {
    append(" ")
    when (this@serializeToYaml) {
        is BooleanNode -> append(value)
        is EnumNode -> append(schemaValue)
        is IntNode -> append(value)
        is PathNode -> append(value.pathString)
        is StringNode -> append(YamlQuoting.quote(value))
    }
    appendLine()
}

fun Key.serializeToYaml(indent: Int): String = buildString {
    append("  ".repeat(indent))
    append(this@serializeToYaml)
    append(":")
}
