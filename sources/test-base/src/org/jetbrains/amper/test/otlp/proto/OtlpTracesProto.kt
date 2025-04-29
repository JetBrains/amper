/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.otlp.proto

import kotlinx.serialization.Serializable

// From: https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/trace/v1/trace.proto

@Serializable
data class TracesData(
    val resourceSpans: List<ResourceSpans> = emptyList(),
)

@Serializable
data class ResourceSpans(
    val resource: Resource? = null,
    val scopeSpans: List<ScopeSpans> = emptyList(),
    val schemaUrl: String? = null,
)

@Serializable
data class ScopeSpans(
    val scope: InstrumentationScope? = null,
    val spans: List<Span> = emptyList(),
    val schemaUrl: String? = null,
)

@Serializable
data class Span(
    val traceId: HexString,
    val spanId: HexString,
    val traceState: String? = null,
    val parentSpanId: HexString? = null,
    val flags: Int? = null,
    val name: String,
    val kind: SpanKind = SpanKind.SPAN_KIND_UNSPECIFIED,
    val startTimeUnixNano: Long,
    val endTimeUnixNano: Long,
    val attributes: List<KeyValue> = emptyList(),
    val droppedAttributesCount: Int = 0,
    val events: List<Event> = emptyList(),
    val droppedEventsCount: Int = 0,
    val links: List<Link> = emptyList(),
    val droppedLinksCount: Int = 0,
    val status: Status? = null,
) {
    private object SpanKindSerializer : EnumToOrdinalSerializer<SpanKind>(SpanKind.entries)

    @Serializable(with = SpanKindSerializer::class)
    enum class SpanKind {
        SPAN_KIND_UNSPECIFIED,
        SPAN_KIND_INTERNAL,
        SPAN_KIND_SERVER,
        SPAN_KIND_CLIENT,
        SPAN_KIND_PRODUCER,
        SPAN_KIND_CONSUMER,
    }

    @Serializable
    data class Event(
        val timeUnixNano: Long,
        val name: String,
        val attributes: List<KeyValue> = emptyList(),
        val droppedAttributesCount: Int = 0,
    )

    @Serializable
    data class Link(
        val traceId: HexString,
        val spanId: HexString,
        val traceState: String? = null,
        val attributes: List<KeyValue> = emptyList(),
        val droppedAttributesCount: Int = 0,
        val flags: Int? = null,
    )
}

@Serializable
data class Status(
    val message: String? = null,
    val code: StatusCode = StatusCode.STATUS_CODE_UNSET,
) {
    private object StatusCodeSerializer : EnumToOrdinalSerializer<StatusCode>(StatusCode.entries)

    @Serializable(with = StatusCodeSerializer::class)
    enum class StatusCode {
        STATUS_CODE_UNSET,
        STATUS_CODE_OK,
        STATUS_CODE_ERROR
    }
}
