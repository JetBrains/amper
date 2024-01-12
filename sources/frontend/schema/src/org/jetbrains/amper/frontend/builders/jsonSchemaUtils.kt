/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.forEachEndAware
import kotlin.reflect.KClass
import kotlin.reflect.KType

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
    uniqueElements: Boolean = true, // TODO handle
    minItems: Int? = null, // TODO handle
    block: () -> String,
) = """
"type": "array",
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
        val enumValues = unwrapKClassOrNull!!.java.enumConstants
        enumValues.toList().forEachEndAware<Any> { isEnd, it ->
            append("\"${(it as? SchemaEnum)?.schemaValue ?: it}\"")
            if (!isEnd) append(",")
        }
        append("]")
    }