/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders.schema

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// This file contains utilities to build [JsonElement] describing JSON schema.

/**
 * Root element of the JSON Schema.
 */
fun JsonSchema(
    `$schema`: String,
    `$id`: String,
    title: String,
    type: String,
    allOf: List<JsonElement>,
    `$defs`: Map<String, JsonElement>,
) = JsonObject(
    "\$schema" to JsonPrimitive(`$schema`),
    "\$id" to JsonPrimitive(`$id`),
    "title" to JsonPrimitive(title),
    "type" to JsonPrimitive(type),
    "allOf" to JsonArray(allOf),
    "\$defs" to JsonObject(`$defs`),
)

/**
 * Copies [JsonObject] with provided title.
 */
fun JsonObject.title(value: String) =
    JsonObject(this + "title".to(JsonPrimitive(value)))

/**
 * Copies [JsonObject] with provided `x-intellij-html-description`.
 */
fun JsonObject.`x-intellij-html-description`(value: String) =
    JsonObject(this + "x-intellij-html-description".to(JsonPrimitive(value)))

/**
 * Copies [JsonObject] with provided `x-intellij-enum-order-sensitive`.
 */
fun JsonObject.`x-intellij-enum-order-sensitive`(value: Boolean?) =
    if (value == true) JsonObject(this + "x-intellij-enum-order-sensitive".to(JsonPrimitive(true)))
    else this

/**
 * `anyOf` JSON Schema element.
 */
fun AnyOfElement(anyOf: List<JsonElement>) = JsonObject("anyOf" to JsonArray(anyOf))
fun AnyOfElement(vararg anyOf: JsonElement) = AnyOfElement(anyOf.toList())

/**
 * `$ref` JSON Schema element.
 */
fun RefElement(typeName: String) = JsonObject("\$ref" to JsonPrimitive("#/\$defs/$typeName"))

/**
 * Scalar JSON Schema element (has only type).
 */
fun ScalarElement(type: String) = JsonObject("type" to JsonPrimitive(type))

/**
 * `enum` JSON Schema element.
 */
fun EnumElement(enumValues: Collection<String>, metadata: Map<String, String>? = null) =
    JsonObject(
        "enum" to enumValues.jsonArray { it },
        "x-intellij-enum-metadata" toNotNull metadata?.ifEmpty { null }?.jsonObject,
    )

/**
 * `array` JSON Schema element.
 */
fun ArrayElement(items: JsonElement, uniqueItems: Boolean = true) = JsonObject(
    "type" to JsonPrimitive("array"),
    "uniqueItems" to JsonPrimitive(uniqueItems),
    "items" to items,
)

/**
 * `object` JSON Schema element.
 */
fun ObjectElement(
    properties: Map<String, JsonElement>? = null,
    patternProperties: Map<String, JsonElement>? = null,
) = JsonObject(
    "type" to JsonPrimitive("object"),
    "additionalProperties" to JsonPrimitive(false),
    "properties" toNotNull properties?.let(::JsonObject),
    "patternProperties" toNotNull patternProperties?.let(::JsonObject),
)

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    explicitNulls = false
    prettyPrint = true
    prettyPrintIndent = "  "
}
val JsonElement.jsonString get() = json.encodeToString(JsonElement.serializer(), this)

internal infix fun <T : Any> String.toNotNull(value: T?) = value?.let { this to it }
internal fun <T> Iterable<T>.jsonArray(transform: (T) -> String) = JsonArray(map { JsonPrimitive(transform(it)) })
internal fun <T> Array<T>.jsonArray(transform: (T) -> String) = asIterable().jsonArray(transform)
internal val Map<String, String>.jsonObject get() = JsonObject(mapValues { JsonPrimitive(it.value) })
internal fun JsonObject(vararg entries: Pair<String, JsonElement>?) = JsonObject(entries.filterNotNull().toMap())