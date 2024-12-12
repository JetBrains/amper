/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.core.forEachEndAware
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.EnumValueFilter
import org.jetbrains.amper.frontend.api.GradleSpecific
import org.jetbrains.amper.frontend.api.PlatformSpecific
import org.jetbrains.amper.frontend.api.ProductTypeSpecific
import org.jetbrains.amper.frontend.api.SchemaDoc
import org.jetbrains.amper.frontend.api.StandaloneSpecific
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

private fun String.addIdentButFirst(ident: String) =
    lines().joinToString(separator = "${System.lineSeparator()}$ident") { it }

fun buildAliasesMapAsList(
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

fun buildObjectWithDynamicKeys(
    buildValueSchema: () -> String,
) = """
"type": "object",
"items": {
  "patternProperties": {
    "^[^@+:]+$": {
      ${buildValueSchema().addIdentButFirst("      ")}
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
    prop: KProperty<*>,
    block: () -> String,
) = """
"${prop.name}": {
  ${extraData(prop)}${block().addIdentButFirst("  ")}
}
""".trim()

private const val extraDataKey = "x-intellij-metadata"

private fun extraData(prop: KProperty<*>): String {
    val extraData = mutableMapOf<String, String>()
    val documentation = prop.findAnnotation<SchemaDoc>()?.doc
    if (!documentation.isNullOrBlank()) {
        extraData["x-intellij-html-description"] = "\"$documentation\""
        extraData["title"] = "\"${turnDocIntoShortForm(documentation)}\""
    }
    buildSpecificsData(prop)?.let { extraData[extraDataKey] = it }
    return if (extraData.isNotEmpty()) {
        extraData.entries.joinToString("") { "\"${it.key}\": ${it.value},\n".addIdentButFirst("  ") }
    } else ""
}

private fun buildSpecificsData(prop: KProperty<*>): String? {
    val platformSpecific = prop.findAnnotation<PlatformSpecific>()
    val productTypeSpecific = prop.findAnnotation<ProductTypeSpecific>()
    val gradleSpecific = prop.findAnnotation<GradleSpecific>()
    val standaloneSpecific = prop.findAnnotation<StandaloneSpecific>()
    val extras =
        if (platformSpecific != null || productTypeSpecific != null || gradleSpecific != null || standaloneSpecific != null) {
            val builder = StringBuilder("{")
            platformSpecific?.let {
                builder.append(
                    "\"platforms\": [${
                        platformSpecific.platforms.joinToString {
                            "\"${it.pretty}\""
                        }
                    }]"
                )
            }
            productTypeSpecific?.let {
                if (platformSpecific != null) builder.append(",")
                builder.append(
                    "\"productTypes\": [${
                        productTypeSpecific.productTypes.joinToString {
                            "\"${it.value}\""
                        }
                    }]")
            }
            gradleSpecific?.let {
                if (platformSpecific != null || productTypeSpecific != null) builder.append(",")
                builder.append("\"gradleSpecific\": true")
            }
            standaloneSpecific?.let {
                if (platformSpecific != null || productTypeSpecific != null || gradleSpecific != null) builder.append(",")
                builder.append("\"standaloneSpecific\": true")
            }
            builder.append("}")
            builder.toString()
        } else null
    return extras
}

private fun turnDocIntoShortForm(documentation: String) = documentation.replace("[Read more]", "").replace(
    Regex("\\([^)]*\\)"), ""
).replace(
    Regex("Read more about \\[([^]]*)]"), ""
).replace(
    Regex("\\[([^]]*)]"), { r -> r.groupValues.getOrNull(1) ?: "" }
).trim().trimStart(')').trimStart('.').trimEnd('.').trim()

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

val integerSchema
    get() = """
"type": "integer"
""".trimIndent()

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
        val propertyToFilter =
            valueFilter?.let { f -> enumClass.memberProperties.firstOrNull { it.name == f.filterPropertyName } }
        enumValues
            .toList()
            .run { if (orderSensitive?.reverse == true) asReversed() else this }
            .filter { it !is SchemaEnum || !it.outdated }
            .filter {
                propertyToFilter == null ||
                        !valueFilter.isNegated && propertyToFilter.getter.call(it) != false ||
                        valueFilter.isNegated && propertyToFilter.getter.call(it) != true
            }
            .forEachEndAware<Any> { isEnd, it ->
                append("\"${(it as? SchemaEnum)?.schemaValue ?: it}\"")
                if (!isEnd) append(",")
            }
        append("]")

        val enumFields = enumClass.java.fields.mapNotNull {
            it.annotations.filterIsInstance<SchemaDoc>().singleOrNull()?.let { a ->
                (it.get(enumClass.java) as? SchemaEnum)?.schemaValue?.let { v -> v to a }
            }
        }

        if (orderSensitive != null) {
            appendLine(",")
            append("\"x-intellij-enum-order-sensitive\": true")
        }

        if (enumFields.isNotEmpty()) {
            appendLine(",")
            append("\"x-intellij-enum-metadata\": {")
            enumFields.forEachIndexed { index, item ->
                append("\"${item.first}\": \"${item.second.doc.let { adjustEnumValueDoc(it) }}\"")
                if (index != enumFields.lastIndex) append(",")
            }
            append("}")
        }
    }

private fun adjustEnumValueDoc(doc: String): String {
    val left = doc.indexOf('[')
    val right = doc.indexOf(']')
    val resultingDoc = (if (left >= 0 && right > left) {
        doc.substring(left + 1, right)
    } else doc).trimStart('(').trimEnd(')').capitalize()
    return if (resultingDoc.isNotEmpty() && resultingDoc[0].isDigit()) "(${resultingDoc})" else resultingDoc
}