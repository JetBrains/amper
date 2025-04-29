/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.otlp.proto

import kotlinx.serialization.Serializable

// From: https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/resource/v1/resource.proto

@Serializable
data class Resource(
    val attributes: List<KeyValue> = emptyList(),
    val droppedAttributesCount: Int = 0
)
