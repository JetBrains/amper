/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.core.forEachEndAware
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.EnumValueFilter
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

fun String.addIdentButFirst(ident: String) =
    lines().joinToString(separator = "${System.lineSeparator()}$ident") { it }

fun buildSchemaKeyBasedCollection(
    block: () -> String,
) = """
"type": "array",
"items": {
  "patternProperties": {
    "^[^@+:]+$": {
      ${block().addIdentButFirst("      ")}
    }
  }
}
""".trim()

fun buildModifierBasedCollection(
    name: String,
    block: () -> String,
) = """
"^$name(@.+)?${'$'}": {
  ${block().addIdentButFirst("  ")}
}
""".trim()

fun buildProperty(
    name: String,
    block: () -> String,
) = """
"$name": {
  ${block().addIdentButFirst("  ")}
}
""".trim()

fun buildSchemaCollection(
    uniqueElements: Boolean = true,
    minItems: Int? = null,
    block: () -> String,
) = """
"type": "array",
${minItems?.let { "\"minItems\": $it,\n" } ?: ""}"uniqueItems": $uniqueElements,
"items": {
  ${block().addIdentButFirst("  ")}
}
""".trim()

val stringSchema
    get() = """
"type": "string"
""".trim()

val booleanSchema
    get() = """
"type": "boolean"
""".trim()

val KClass<*>.jsonDef: String get() = simpleName!!
val KClass<*>.asReferenceTo get() = "\"\$ref\": \"#/\$defs/${this.jsonDef}\""

fun <T> Collection<T>.wrapInAnyOf(block: (T) -> String) = buildString {
    if (size == 1) {
        append(block(this@wrapInAnyOf.first()))
    } else {
        appendLine("\"anyOf\": [")
        forEachEndAware { isEnd, it ->
            appendLine("  {")
            appendLine(block(it).prependIndent("    "))
            append("  }")
            if (!isEnd) appendLine(",") else appendLine()
        }
        append("]")
    }
}

val KType.enumSchema
    get() = buildString {
        append("\"enum\": [")
        val enumClass = unwrapKClassOrNull!!
        val enumValues = enumClass.java.enumConstants
        val orderSensitive = enumClass.findAnnotation<EnumOrderSensitive>()
        val valueFilter = enumClass.findAnnotation<EnumValueFilter>()
        val propertyToFilter = valueFilter?.let { f -> enumClass.memberProperties.firstOrNull { it.name == f.filterPropertyName } }
        enumValues
            .toList()
            .run { if (orderSensitive?.reverse == true) asReversed() else this  }
            .filter { it !is SchemaEnum || !it.outdated }
            .filter { propertyToFilter == null || propertyToFilter.getter.call(it) != false }
            .forEachEndAware<Any> { isEnd, it ->
                append("\"${(it as? SchemaEnum)?.schemaValue ?: it}\"")
                if (!isEnd) append(",")
            }
        append("]")

        if (orderSensitive != null) {
            appendLine(",")
            append("\"x-intellij-enum-order-sensitive\": true")
        }
    }