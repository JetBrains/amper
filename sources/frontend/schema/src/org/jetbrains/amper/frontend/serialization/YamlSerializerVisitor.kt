/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.serialization

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.HiddenFromCompletion
import org.jetbrains.amper.frontend.api.IgnoreForSchema
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.ResolvedReferenceTrace
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableValue
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import kotlin.reflect.full.hasAnnotation

internal fun interface SchemaValueFilter {
    fun shouldInclude(valueDelegate: SchemaValueDelegate<*>): Boolean
}

/**
 * Serializes a [SchemaNode] to a YAML string that looks like how it would appear in Amper files.
 */
internal fun SchemaNode.serializeAsAmperYaml(
    indent: String = "  ",
    filter: SchemaValueFilter = SchemaValueFilter { true },
): String = buildString {
    YamlSerializerVisitor(
        builder = this@buildString,
        indent = indent,
        filter = filter,
    ).visit(this@serializeAsAmperYaml)
}.trimIndent() // the invariants for object serialization create a leading blank line and one general indentation level

// Invariants
//   1. at the beginning of a visit, we're already at the correct indentation level, thus we must NOT add any indent
//     (to allow visiting after ': ' or '- ')
//   2. every new line added in the middle of a visit must be prepended with the current indent (to respect nesting)
//   3. when we're done visiting an element, we must be back on a new line with current indent
//     (to enable calling multiple visits in sequence)
private class YamlSerializerVisitor(
    private val builder: StringBuilder,
    private val indent: String,
    private val filter: SchemaValueFilter,
) : SchemaValuesVisitor() {

    private var currentIndent: String = ""

    override fun visitCollection(collection: Collection<*>) {
        // we need the inline [] representation in the empty case to avoid having nothing after the property name
        if (collection.isEmpty()) {
            builder.append("[]")
            appendNewLineWithIndent()
            return
        }
        appendNestedBlock {
            collection.forEach {
                builder.append("- ")
                withIndent("  ") { // that's the dimension of "- ", not this.indent
                    visit(it)
                }
            }
        }
    }

    override fun visitMap(map: Map<*, *>) {
        // we need the inline {} representation in the empty case to avoid having nothing after the property name
        if (map.isEmpty()) {
            builder.append("{}")
            appendNewLineWithIndent()
            return
        }
        appendNestedBlock {
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
        appendNestedBlock {
            super.visitSchemaNode(node)
        }
    }

    override fun visitSchemaValueDelegate(schemaValue: SchemaValueDelegate<*>) {
        // We don't care about such properties
        if (schemaValue.property.hasAnnotation<HiddenFromCompletion>()) return
        if (schemaValue.property.hasAnnotation<IgnoreForSchema>()) return
        if (!filter.shouldInclude(schemaValue)) return

        builder.append(schemaValue.property.name)
        builder.append(": ")
        visit(schemaValue.value)

        val traceDescription = schemaValue.traceDescription()
        if (traceDescription != null && schemaValue.value.isSingleLineInYaml()) {
            appendCommentToPreviousLine("[$traceDescription]")
        }
    }

    override fun visitTraceableValue(traceableValue: TraceableValue<*>) {
        super.visitTraceableValue(traceableValue)

        val traceDescription = traceableValue.trace.traceDescription()
        if (traceDescription != null && traceableValue.value.isSingleLineInYaml()) {
            appendCommentToPreviousLine("[$traceDescription]")
        }
    }

    private fun appendCommentToPreviousLine(comment: String) {
        builder.deleteRange(builder.indexOfLast { it == '\n' }, builder.length)
        builder.append("  # $comment")
        appendNewLineWithIndent()
    }

    private fun Any?.isSingleLineInYaml() =
        when (this) {
            is SchemaNode -> false
            is Collection<*> -> isEmpty()
            is Map<*, *> -> isEmpty()
            else -> true
        }

    override fun visitSchemaEnumValue(schemaEnum: SchemaEnum) {
        builder.append(schemaEnum.schemaValue)
        appendNewLineWithIndent()
    }

    override fun visitPrimitiveLike(other: Any?) {
        builder.append(other)
        appendNewLineWithIndent()
    }

    private fun appendNestedBlock(visitChildren: () -> Unit) {
        withIndent(indent) {
            appendNewLineWithIndent()
            visitChildren()
        }
    }

    private fun withIndent(indent: String, visitChildren: () -> Unit) {
        currentIndent += indent
        visitChildren()
        currentIndent = currentIndent.removeSuffix(indent)
        builder.deleteSuffix(indent) // there was one extra indent from visitChildren()'s invariant
    }

    private fun appendNewLineWithIndent() {
        builder.appendLine().append(currentIndent)
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

private fun SchemaValueDelegate<*>.traceDescription(): String? = trace.traceDescription()

// This is more complicated than it needs to be because traces don't contain information about derived defaults.
// We therefore have to have a handle on the Default value from schema delegates, which is not always available.
private fun Trace.traceDescription(): String? =
    when (this) {
        is PsiTrace -> psiElement.containingFilename()
        is DefaultTrace -> "default"
        is BuiltinCatalogTrace -> "from built-in catalog"
        is ResolvedReferenceTrace -> description
        is TransformedValueTrace -> description
    }

private fun PsiElement.containingFilename() = ReadAction.compute<String, Throwable> { containingFile.name }
