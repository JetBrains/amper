/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.foojay.serialization

import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

internal object LenientBooleanSerializer : JsonTransformingSerializer<Boolean?>(Boolean.serializer().nullable) {

    override fun transformDeserialize(element: JsonElement): JsonElement {
        check(element is JsonPrimitive) { "Expected JsonPrimitive, got ${element::class.simpleName}" }
        return when (element.content) {
            "unknown" -> JsonNull
            "yes" -> JsonPrimitive(true)
            "no" -> JsonPrimitive(false)
            else -> element
        }
    }
}

internal object EmptyStringAsNullSerializer : JsonTransformingSerializer<String?>(String.serializer().nullable) {

    override fun transformDeserialize(element: JsonElement): JsonElement {
        check(element is JsonPrimitive) { "Expected JsonPrimitive, got ${element::class.simpleName}" }
        return when (element.content) {
            "" -> JsonNull
            else -> element
        }
    }
}
