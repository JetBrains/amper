/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.contexts.DefaultContext
import org.jetbrains.amper.frontend.contexts.TestCtx
import org.jetbrains.amper.frontend.schema.BomDependency
import org.jetbrains.amper.frontend.tree.ListNode
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.TreeRefiner
import org.jetbrains.amper.frontend.tree.copy
import org.jetbrains.amper.frontend.tree.declaration
import org.jetbrains.amper.frontend.types.SchemaType
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

typealias Key = String

val treeRefiner = TreeRefiner()

fun MappingNode.serializeToYaml(): String = buildString {
    val refinedMain = treeRefiner.refineTree(this@serializeToYaml, listOf())
    val main = refinedMain.filterByContext(DefaultContext.ReactivelySet)
    val refinedTest = treeRefiner.refineTree(this@serializeToYaml, listOf(TestCtx))
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

        val collapsibleValue = if (collapsiblePropertyValue.type is SchemaType.EnumType) {
            (collapsiblePropertyValue.value as SchemaEnum).schemaValue
        } else {
            collapsiblePropertyValue.value
        }

        val theRest = children.filter { it !== collapsibleProperty }
        append(" ")
        if (declaration?.createInstance() is BomDependency) {
            append("bom: ")
        }
        if (collapsibleValue is Path) {
            append(collapsibleValue.invariantSeparatorsPathString)
        } else {
            append(collapsibleValue)
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
    when (type) {
        is SchemaType.EnumType -> {
            when(value) {
                is TraceableEnum<*> -> append(((value as TraceableEnum<*>).value as SchemaEnum).schemaValue)
                is SchemaEnum -> append((value as SchemaEnum).schemaValue)
            }
        }
        is SchemaType.StringType -> {
            when (value) {
                is String -> append(YamlQuoting.quote(value as String))
                is TraceableString -> append(YamlQuoting.quote((value as TraceableString).value))
            }
        }
        else -> append(value)
    }
    appendLine()
}

fun Key.serializeToYaml(indent: Int): String = buildString {
    append("  ".repeat(indent))
    append(this@serializeToYaml)
    append(":")
}
