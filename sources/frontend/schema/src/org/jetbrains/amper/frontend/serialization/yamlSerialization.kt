/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.serialization

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.DerivedValueTrace
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.CompleteListNode
import org.jetbrains.amper.frontend.tree.CompleteMapNode
import org.jetbrains.amper.frontend.tree.CompleteObjectNode
import org.jetbrains.amper.frontend.tree.CompletePropertyKeyValue
import org.jetbrains.amper.frontend.tree.CompleteTreeNode
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.IntNode
import org.jetbrains.amper.frontend.tree.NullLiteralNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.StringNode
import org.jetbrains.amper.frontend.tree.schemaValue

internal fun interface SchemaValueFilter {
    fun shouldInclude(keyValue: CompletePropertyKeyValue): Boolean
}

interface YamlTheme {
    enum class SpecialElementType {
        Comment,
        PropertyName,
    }

    /**
     * Returns the given [text], optionally with ANSI color codes to colorize it as desired based on the given [type].
     */
    fun colorize(text: String, type: SpecialElementType): String = text

    /**
     * A default theme that doesn't add any color.
     */
    companion object NoColor : YamlTheme
}

/**
 * Serializes a [SchemaNode] to a YAML string that looks like how it would appear in Amper files.
 */
internal fun SchemaNode.serializeAsAmperYaml(
    indent: String = "  ",
    filter: SchemaValueFilter = SchemaValueFilter { true },
    theme: YamlTheme = YamlTheme.NoColor,
): String = YamlSerializer(indent = indent, filter = filter, theme = theme).serialize(this.backingTree)

internal class YamlSerializer(
    private val indent: String = "  ",
    private val filter: SchemaValueFilter = SchemaValueFilter { true },
    private val theme: YamlTheme = YamlTheme.NoColor,
) {
    fun serialize(value: CompleteTreeNode): String = when (value) {
        is CompleteListNode -> serializeList(value)
        is CompleteMapNode -> serializeMap(value)
        is CompleteObjectNode -> serialize(value)
        is NullLiteralNode -> serializeScalar(text = "null", value.trace)
        is BooleanNode -> serializeScalar(text = value.value.toString(), value.trace)
        is EnumNode -> serializeScalar(text = value.schemaValue, value.trace)
        is IntNode -> serializeScalar(text = value.value.toString(), value.trace)
        is PathNode -> serializeScalar(text = value.value.joinToString("/"), value.trace) // OS-agnostic
        is StringNode -> serializeScalar(text = value.value, value.trace)
    }

    private fun serializeScalar(text: String, trace: Trace): String = text + traceComment(trace)

    private fun serializeList(list: CompleteListNode): String {
        if (list.children.isEmpty()) {
            return "[]" + traceComment(list.trace)
        }
        return list.children.joinToString("\n") { item ->
            val serializedItem = serialize(item)
            val indentedItem = serializedItem.prependIndent("  ") // this is the size of "- " not an indent level
            "- ${indentedItem.drop(2)}" // we replace the 2 spaces of the first line to use "- " instead
        }
    }

    private fun serializeMap(map: CompleteMapNode): String {
        if (map.refinedChildren.isEmpty()) {
            return "{}" + traceComment(map.trace)
        }
        return map.refinedChildren.entries.joinToString("\n") { (key, kv) ->
            serializeKeyValue(
                key = "\"$key\"", // we quote map keys to distinguish them from property names and avoid escaping
                value = kv.value,
            )
        }
    }

    private fun serialize(value: CompleteObjectNode): String {
        val filteredDelegates = value.refinedChildren.values.filter {
            !it.propertyDeclaration.isHiddenFromCompletion
                    && filter.shouldInclude(it)
        }
        if (filteredDelegates.isEmpty()) {
            return "{}" + traceComment(value.trace)
        }
        return filteredDelegates.sortedBy { it.key }.joinToString("\n") { kv ->
            serializeKeyValue(
                key = theme.colorize(kv.key, YamlTheme.SpecialElementType.PropertyName),
                value = kv.value,
            )
        }
    }

    private fun serializeKeyValue(key: String, value: CompleteTreeNode) = buildString {
        append(key)
        append(':')
        val serializedValue = serialize(value)
        if (value.isScalarLike() || serializedValue.startsWith("[]") || serializedValue.startsWith("{}")) {
            append(' ')
            append(serializedValue)
        } else {
            appendLine()
            append(serializedValue.prependIndent(indent))
        }
    }

    private fun traceComment(trace: Trace): String {
        val traceDescription = trace.traceDescription()
        return theme.colorize("  # $traceDescription", YamlTheme.SpecialElementType.Comment)
    }

    private fun CompleteTreeNode.isScalarLike(): Boolean = when (this) {
        is ScalarNode, is NullLiteralNode -> true
        else -> false
    }
}

private fun Trace.traceDescription(): String = when (this) {
    is PsiTrace -> psiElement.containingFilename()
    is DefaultTrace -> "default"
    is BuiltinCatalogTrace -> "from built-in catalog"
    is DerivedValueTrace -> buildString {
        append(description)
        val fileHint = sourceValue.extractPsiElementOrNull()?.containingFilename()
        if (fileHint != null) {
            this.append(" @ $fileHint")
        }
    }
}

private fun PsiElement.containingFilename() = ReadAction.compute<String, Throwable> { containingFile.name }
