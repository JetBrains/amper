/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helpers

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.ModuleTasksPart
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.api.DerivedValueTrace
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.CompleteListNode
import org.jetbrains.amper.frontend.tree.CompleteMapNode
import org.jetbrains.amper.frontend.tree.CompleteObjectNode
import org.jetbrains.amper.frontend.tree.CompletePropertyKeyValue
import org.jetbrains.amper.frontend.tree.CompleteTreeVisitor
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.IntNode
import org.jetbrains.amper.frontend.tree.NullLiteralNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.tree.schemaValue
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

/**
 * Prints a human-readable string representation of this module, for comparison with gold files.
 *
 * NB: the default value for [printDefaults] is `false`, so that golden files are as comprehensible as possible
 * and only reflect the values overridden in the corresponding module file. There is a dedicated set of tests
 * ([org.jetbrains.amper.frontend.schema.DefaultsTest]) that checks the defaults for all the
 * product types. If you are interested in how defaults are configured in some non-trivial scenario, please, prefer
 * putting tests with `printDefaults = true` in the `DefaultsTest` suite.
 *
 * @param printDefaults whether to print default values into gold files or omit them. NB: derived properties are
 *   always printed, even though they are marked as `<default>` in gold files.
 */
// We don't use the visitor from the root for 2 reasons:
//   1. this limits changes to gold files (we just replace the "parts" values with settings, but the rest of the format
//      stays as it was)
//   2. as a whole, the AmperModule object contains cross-references (and even cycles) which are not nice to print
internal fun AmperModule.prettyPrintForGoldFile(printDefaults: Boolean = false): String = buildString {
    appendLine("Fragments:")
    for (fragment in fragments.sortedBy { it.name }) {
        appendLine("  ${fragment.name}")
        appendLine("    External dependencies:")
        for (dependency in fragment.externalDependencies.sortedBy { it.toString() }) {
            appendLine("      $dependency")
        }
        appendLine("    Src folders:")
        for (source in fragment.sourceRoots) {
            appendLine("      ${source.relativeTo(this@prettyPrintForGoldFile.source.moduleDir).invariantSeparatorsPathString}")
        }
        appendLine("    Fragment dependencies:")
        for (dependency in fragment.fragmentDependencies) {
            appendLine("      ${dependency.target.name} (${dependency.type})")
        }
        append("    Settings: ")
        appendLine(prettyPrintForGoldFile(fragment.settings, printDefaults).prependIndent("    ").trim())
        appendLine()
    }
    appendLine("Artifacts:")
    for (artifact in artifacts.sortedBy { it.name }) {
        appendLine("  isTest: ${artifact.isTest}")
        appendLine("  ${artifact.platforms.map { it.schemaValue }.sorted()}")
        appendLine("    Fragments:")
        for (fragment in artifact.fragments.sortedBy { it.name }) {
            appendLine("      ${fragment.name}")
        }
    }

    val repositories = parts[RepositoriesModulePart::class.java]?.mavenRepositories
    if (!repositories.isNullOrEmpty()) {
        appendLine("Repositories:")
        repositories.forEach {
            appendLine("  - id: ${it.id}")
            appendLine("    url: ${it.url}")
            appendLine("    publish: ${it.publish}")
            appendLine("    resolve: ${it.resolve}")
            appendLine("    username: ${it.userName}")
            appendLine("    password: ${it.password}")
        }
    }

    val taskSettingsMap = parts[ModuleTasksPart::class.java]?.settings ?: emptyMap()
    if (taskSettingsMap.isNotEmpty()) {
        appendLine("Task:")
        taskSettingsMap.forEach { (taskName, settings) ->
            appendLine("  - name: $taskName")
            appendLine("    dependsOn: ${settings.dependsOn.joinToString(", ")}")
        }
    }
}

private fun prettyPrintForGoldFile(value: SchemaNode, printDefaults: Boolean): String = buildString {
    HumanReadableSerializerVisitor(builder = this@buildString, indent = "  ", printDefaults).visit(value.backingTree)
}

// Invariants
//   - the beginning of a visit does NOT start with any indent (to allow visiting after ': ' or '- ')
//   - every new line added in the middle of a visit starts with at least the current indent (to respect nesting)
//   - when we're done visiting an element, we must be back on a new line with current indent (to enable calling all
//     children one by one from the parent class, and still have some separation)
private class HumanReadableSerializerVisitor(
    private val builder: StringBuilder,
    private val indent: String,
    private val printDefaults: Boolean,
) : CompleteTreeVisitor<Unit> {

    private var currentIndent: String = ""

    override fun visitList(node: CompleteListNode) {
        appendBlock(start = "[", end = "]") {
            node.children.forEach {
                builder.append("- ")
                currentIndent += "  " // that's the dimension of "- ", not the indent
                visit(it)
                currentIndent = currentIndent.removeSuffix("  ") // that's the dimension of "- ", not the indent
                builder.deleteSuffix("  ") // go back for the next '- '
            }
        }
    }

    override fun visitMap(node: CompleteMapNode) {
        appendBlock(start = "{", end = "}") {
            node.refinedChildren.forEach { (k, kv) ->
                builder.append('"')
                builder.append(k)
                builder.append('"')
                builder.append(": ")
                visit(kv.value)
            }
        }
    }

    override fun visitObject(node: CompleteObjectNode) {
        appendBlock(start = "{", end = "}") {
            node.refinedChildren.values.forEach(::visitProperty)
        }
    }

    private fun appendBlock(start: String, end: String, visitChildren: () -> Unit) {
        builder.appendLine(start)
        currentIndent += indent
        builder.append(currentIndent)
        visitChildren()
        currentIndent = currentIndent.removeSuffix(indent)
        builder.deleteSuffix(indent) // there was one extra indent from visitChildren()'s invariant
        builder.appendLine(end).append(currentIndent)
    }

    private fun visitProperty(keyValue: CompletePropertyKeyValue) {
        // We don't care about such properties
        if (keyValue.propertyDeclaration.isHiddenFromCompletion) return

        val valueTrace = keyValue.value.trace
        val isSetToDefault = valueTrace.isDefault
        val isDerived = valueTrace is DerivedValueTrace

        /*
        We still want to print derived properties because if the logic of its calculation is changed we want to notice
        it in the tests that override the property we depend on.

        E.g., having this schema:
        ```kotlin
        val a by value(42)
        val b by derivedValue(::a) { a + 1 }
        ```
        The defaults tests will have `a = 42 <default>` and `b = 43 <default>`.
        Changing `b` to:
        ```kotlin
        val b by derivedValue(::a) { if (a = 0) 42 else a + 1 }
        ```
        Will still result in `a = 42 <default>` and `b = 43 <default>`.

        However, if we had a test with `a: 0`, we'd want to reflect that in the first version of the schema `b` is
        `b = 1 <default>` and in the second version it's `b = 42 <default>`.
        */
        if (isSetToDefault && !isDerived && !printDefaults) return

        builder.append(keyValue.key)
        builder.append(": ")
        if (isSetToDefault) {
            builder.append("<default> ")
        }
        visit(keyValue.value)
    }

    override fun visitNull(node: NullLiteralNode) = visitPrimitiveLike("null")

    override fun visitScalar(node: ScalarNode): Unit = when (node) {
        is BooleanNode -> visitPrimitiveLike(node.value)
        is IntNode -> visitPrimitiveLike(node.value)
        is PathNode -> visitPrimitiveLike(node.value.pathString)
        is StringNode -> visitPrimitiveLike(node.value)
        is EnumNode -> visitPrimitiveLike(node.schemaValue)
    }

    private fun visitPrimitiveLike(other: Any) {
        builder.appendLine(other).append(currentIndent)
    }
}

private fun StringBuilder.deleteSuffix(suffix: String) {
    if (endsWith(suffix)) {
        deleteLast(suffix.length)
    }
}

private fun StringBuilder.deleteLast(n: Int) {
    deleteRange(startIndex = length - n, length)
}
