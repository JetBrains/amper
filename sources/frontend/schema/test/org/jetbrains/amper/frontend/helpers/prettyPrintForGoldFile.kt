/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helpers

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.ModuleTasksPart
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.api.DerivedValueTrace
import org.jetbrains.amper.frontend.api.HiddenFromCompletion
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.isDefault
import kotlin.reflect.full.hasAnnotation

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
        appendLine("    Src folder: ${fragment.src.fileName}")
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

private fun prettyPrintForGoldFile(value: Any, printDefaults: Boolean): String = buildString {
    HumanReadableSerializerVisitor(builder = this@buildString, indent = "  ", printDefaults).visit(value)
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
) : SchemaValuesVisitor() {

    private var currentIndent: String = ""

    override fun visitCollection(collection: Collection<*>) {
        appendBlock(start = "[", end = "]") {
            collection.forEach {
                builder.append("- ")
                currentIndent += "  " // that's the dimension of "- ", not the indent
                visit(it)
                currentIndent = currentIndent.removeSuffix("  ") // that's the dimension of "- ", not the indent
                builder.deleteSuffix("  ") // go back for the next '- '
            }
        }
    }

    override fun visitMap(map: Map<*, *>) {
        appendBlock(start = "{", end = "}") {
            map.forEach { (k, v) ->
                builder.append('"')
                builder.append(k.toString())
                builder.append('"')
                builder.append(": ")
                visit(v)
            }
        }
    }

    override fun visitSchemaNode(node: SchemaNode) {
        appendBlock(start = "{", end = "}") {
            super.visitSchemaNode(node)
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

    override fun visitSchemaValueDelegate(schemaValue: SchemaValueDelegate<*>) {
        // We don't care about such properties
        if (schemaValue.property.hasAnnotation<HiddenFromCompletion>()) return

        val isSetToDefault = schemaValue.trace.isDefault
        val isDerived = schemaValue.trace is DerivedValueTrace

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

        builder.append(schemaValue.property.name)
        builder.append(": ")
        if (isSetToDefault) {
            builder.append("<default> ")
        }
        visit(schemaValue.value)
    }

    override fun visitPrimitiveLike(other: Any?) {
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
