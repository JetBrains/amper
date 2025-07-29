/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema.helper

import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.ModuleTasksPart
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.ValueDelegateBase
import org.jetbrains.amper.frontend.schema.SerializationSettings
import java.nio.file.Path
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

/**
 * Prints a human-readable string representation of this module, for comparison with gold files.
 */
// We don't use the visitor from the root for 2 reasons:
//   1. this limits changes to gold files (we just replace the "parts" values with settings, but the rest of the format
//      stays as it was)
//   2. as a whole, the AmperModule object contains cross-references (and even cycles) which are not nice to print
internal fun AmperModule.prettyPrintForGoldFile(): String = buildString {
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
        appendLine(prettyPrintForGoldFile(fragment.settings).prependIndent("    ").trim())
        appendLine()
    }
    appendLine("Artifacts:")
    for (artifact in artifacts.sortedBy { it.name }) {
        appendLine("  isTest: ${artifact.isTest}")
        appendLine("  ${artifact.platforms}")
        appendLine("    Fragments:")
        for (fragment in artifact.fragments) {
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

private fun prettyPrintForGoldFile(value: Any): String = buildString {
    HumanReadableSerializerVisitor(builder = this@buildString, indent = "  ").visit(value)
}

// Invariants
//   - the beginning of a visit does NOT start with any indent (to allow visiting after ': ' or '- ')
//   - every new line added in the middle of a visit starts with at least the current indent (to respect nesting)
//   - when we're done visiting an element, we must be back on a new line with current indent (to enable calling all
//     children one by one from the parent class, and still have some separation)
private class HumanReadableSerializerVisitor(
    private val builder: StringBuilder,
    private val indent: String,
) : SchemaValuesVisitor() {

    private var currentIndent: String = ""

    private val visited = mutableSetOf<Any>()

    override fun visit(it: Any?) {
        if (visitTraceable(it)) return
        if (!it.isPrettifiedWithToString()) {
            // we have to detect cycles for complex objects
            // we exclude the empty collections/maps because we don't want to count those as a cycle
            // TODO instead of checking for _duplicates_ we should check for real _cycles_ (only keeping track of
            //  objects within which we are, not the ones that appear in other places in the file)
            if (it in visited &&
                !it.asSafely<Collection<*>>().isNullOrEmpty() &&
                !it.asSafely<Map<*, *>>().isNullOrEmpty()
            ) {
                builder.appendLine("<cycle>").append(currentIndent)
                return
            }
            visited.add(it)
        }
        super.visit(it)
    }

    private fun visitTraceable(it: Any?): Boolean {
        when (it) {
            is TraceableString -> {
                super.visit(it.value)
                return true
            }

            is TraceableEnum<*> -> {
                super.visit(it.value)
                return true
            }

            is TraceablePath -> {
                super.visit(it.value)
                return true
            }

            else -> return false
        }
    }

    override fun visitCollection(it: Collection<*>) {
        appendBlock(start = "[", end = "]") {
            it.forEach {
                builder.append("- ")
                currentIndent += "  " // that's the dimension of "- ", not the indent
                visit(it)
                currentIndent = currentIndent.removeSuffix("  ") // that's the dimension of "- ", not the indent
                builder.deleteSuffix("  ") // go back for the next '- '
            }
        }
    }

    override fun visitMap(it: Map<*, *>) {
        appendBlock(start = "{", end = "}") {
            it.forEach { (k, v) ->
                builder.append('"')
                builder.append(k.toString())
                builder.append('"')
                builder.append(": ")
                visit(v)
            }
        }
    }

    override fun visitNode(it: SchemaNode) {
        appendBlock(start = "{", end = "}") {
            super.visitNode(it)
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

    override fun visitValue(it: ValueDelegateBase<*>) {
        builder.append(it.property.name)
        builder.append(": ")
        // TODO Remove this hack after removing computable dependant defaults.
        val isDefaultSerialize = it.property == SerializationSettings::enabled && it.trace is DefaultTrace
        if (it.trace == DefaultTrace || isDefaultSerialize) {
            builder.append("<default> ")
        }
        visit(it.value)
    }

    override fun visitOther(it: Any?) {
        if (it.isPrettifiedWithToString()) {
            builder.appendLine(it).append(currentIndent)
        } else {
            visitObject(it)
        }
    }

    private fun visitObject(obj: Any) {
        // this should usually be avoided, probably by adding more types to isPrettifiedWithToString()
        System.err.println("WARN: relying on general object reflection in gold file for ${obj::class.qualifiedName}")
        obj::class.memberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .sortedBy { it.name }
            .forEach { prop ->
                visitProperty(receiver = obj, prop)
            }
    }

    private fun <T : Any> visitProperty(receiver: Any, prop: KProperty1<T, *>) {
        builder.append(prop.name)
        builder.append(": ")
        @Suppress("UNCHECKED_CAST")
        visit(prop.get(receiver as T))
    }
}

/**
 * Returns true if this value is prettified as a simple string.
 */
@OptIn(ExperimentalContracts::class)
private fun Any?.isPrettifiedWithToString(): Boolean {
    contract {
        returns(false) implies (this@isPrettifiedWithToString != null)
    }
    return when (this) {
        null,
        is Boolean,
        is Short,
        is Int,
        is Long,
        is Float,
        is Double,
        is String,
        is Enum<*>,
        is Path -> true

        else -> this::class.isData
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
