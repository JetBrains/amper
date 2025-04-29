/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.otlp.proto

import kotlinx.serialization.Serializable

// From: https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/common/v1/common.proto

@Serializable
data class AnyValue(
    val stringValue: String? = null,
    val boolValue: Boolean? = null,
    val intValue: Long? = null,
    val doubleValue: Double? = null,
    val arrayValue: ArrayValue? = null,
    val kvlistValue: KeyValueList? = null,
    val bytesValue: HexString? = null
)

@Serializable
data class ArrayValue(
    val values: List<AnyValue> = emptyList()
)

@Serializable
data class KeyValueList(
    val values: List<KeyValue> = emptyList()
)

@Serializable
data class KeyValue(
    val key: String,
    val value: AnyValue
)

@Serializable
data class InstrumentationScope(
    val name: String = "",
    val version: String? = null,
    val attributes: List<KeyValue> = emptyList(),
    val droppedAttributesCount: Int = 0
)
