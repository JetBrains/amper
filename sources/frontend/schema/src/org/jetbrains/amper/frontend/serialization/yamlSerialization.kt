/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.serialization

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.DerivedValueTrace
import org.jetbrains.amper.frontend.api.HiddenFromCompletion
import org.jetbrains.amper.frontend.api.IgnoreForSchema
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableValue
import org.jetbrains.amper.frontend.api.propertyDelegates
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import java.nio.file.Path
import kotlin.reflect.full.hasAnnotation

internal fun interface SchemaValueFilter {
    fun shouldInclude(valueDelegate: SchemaValueDelegate<*>): Boolean
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
): String = YamlSerializer(indent = indent, filter = filter, theme = theme).serialize(this, trace = trace)

internal class YamlSerializer(
    private val indent: String = "  ",
    private val filter: SchemaValueFilter = SchemaValueFilter { true },
    private val theme: YamlTheme = YamlTheme.NoColor,
) {
    fun serialize(value: Any?, trace: Trace?): String = when (value) {
        is SchemaEnum -> serializeEnum(value, trace) // must be before Enum to be taken into account
        null,
        is Boolean,
        is Short,
        is Int,
        is Long,
        is Float,
        is Double,
        is String,
        is Enum<*> -> serializeScalar(text = value.toString(), trace)
        is Path -> serializeScalar(text = value.joinToString("/"), trace) // OS-agnostic
        is Collection<*> -> serializeList(value, trace)
        is Map<*, *> -> serializeMap(value, trace)
        is SchemaNode -> serialize(value)
        is TraceableValue<*> -> serializeTraceableValue(value)
        else -> error("Unsupported type: ${value::class}")
    }

    private fun serializeEnum(value: SchemaEnum, trace: Trace?): String = serializeScalar(value.schemaValue, trace)

    private fun serializeScalar(text: String, trace: Trace?): String = text + traceComment(trace)

    private fun serializeList(list: Collection<*>, trace: Trace?): String {
        if (list.isEmpty()) {
            return "[]" + traceComment(trace)
        }
        return list.joinToString("\n") { item ->
            // we don't know the trace of specific items here, hopefully they are Traceable themselves
            val serializedItem = serialize(item, trace = null)
            val indentedItem = serializedItem.prependIndent("  ") // this is the size of "- " not an indent level
            "- ${indentedItem.drop(2)}" // we replace the 2 spaces of the first line to use "- " instead
        }
    }

    private fun serializeMap(map: Map<*, *>, trace: Trace?): String {
        if (map.isEmpty()) {
            return "{}" + traceComment(trace)
        }
        return map.entries.joinToString("\n") { (key, value) ->
            val keyText = when (key) {
                is CharSequence -> key
                is TraceableValue<*> -> key.value
                else -> error("Unsupported key type in map: ${key?.let { it::class.qualifiedName }}")
            }
            serializeKeyValue(
                key = "\"$keyText\"", // we quote map keys to distinguish them from property names and avoid escaping
                value = value,
                trace = null, // we don't know the trace of specific entries here, hopefully they are Traceable
            )
        }
    }

    private fun serialize(value: SchemaNode): String {
        val filteredDelegates = value.propertyDelegates.filter {
            !it.property.hasAnnotation<HiddenFromCompletion>()
                    && !it.property.hasAnnotation<IgnoreForSchema>()
                    && filter.shouldInclude(it)
        }
        if (filteredDelegates.isEmpty()) {
            return "{}" + traceComment(value.trace)
        }
        return filteredDelegates.sortedBy { it.property.name }.joinToString("\n") { delegate ->
            serializeKeyValue(
                key = theme.colorize(delegate.property.name, YamlTheme.SpecialElementType.PropertyName),
                value = delegate.value,
                trace = delegate.trace,
            )
        }
    }

    private fun serializeKeyValue(key: String, value: Any?, trace: Trace?) = buildString {
        append(key)
        append(':')
        val serializedValue = serialize(value, trace = trace)
        if (value.isScalarLike() || serializedValue.startsWith("[]") || serializedValue.startsWith("{}")) {
            append(' ')
            append(serializedValue)
        } else {
            appendLine()
            append(serializedValue.prependIndent(indent))
        }
    }

    private fun serializeTraceableValue(value: TraceableValue<*>): String = serialize(value.value, value.trace)

    private fun traceComment(trace: Trace?): String {
        val traceDescription = trace?.traceDescription() ?: return ""
        return theme.colorize("  # $traceDescription", YamlTheme.SpecialElementType.Comment)
    }

    private fun Any?.isScalarLike(): Boolean = when (this) {
        null,
        is Boolean,
        is Short,
        is Int,
        is Long,
        is Float,
        is Double,
        is String,
        is Enum<*>,
        is SchemaEnum,
        is Path -> true
        is TraceableValue<*> -> value.isScalarLike()
        else -> false
    }
}

private fun Trace.traceDescription(): String? = when (this) {
    is PsiTrace -> psiPointer.virtualFile.name
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
