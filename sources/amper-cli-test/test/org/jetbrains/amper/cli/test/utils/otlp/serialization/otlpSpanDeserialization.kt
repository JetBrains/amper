/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package org.jetbrains.amper.cli.test.utils.otlp.serialization

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.serialization.json.Json
import org.jetbrains.amper.cli.test.utils.otlp.proto.AnyValue
import org.jetbrains.amper.cli.test.utils.otlp.proto.KeyValue
import org.jetbrains.amper.cli.test.utils.otlp.proto.ResourceSpans
import org.jetbrains.amper.cli.test.utils.otlp.proto.Span
import org.jetbrains.amper.cli.test.utils.otlp.proto.Status
import org.jetbrains.amper.cli.test.utils.otlp.proto.TracesData

fun Json.decodeOtlpTraces(jsonLines: List<String>): List<SpanData> = jsonLines.flatMap { decodeJsonTraces(it) }

private fun Json.decodeJsonTraces(json: String): List<SpanData> =
    decodeFromString<TracesData>(json).resourceSpans.flatMap { it.toSpans() }

private fun ResourceSpans.toSpans(): List<SpanData> {
    val resource = resource ?: error("resource is required")
    return scopeSpans.flatMap { it.spans }.map { DeserializedSpanData(span = it, resource = resource) }
}

private class DeserializedSpanData(
    private val span: Span,
    private val resource: org.jetbrains.amper.cli.test.utils.otlp.proto.Resource,
) : SpanData {

    private val context = createContext(span.traceId, span.spanId, span.flags, span.traceState)
    private val attributes = span.attributes.toAttributes()

    override fun getName(): String? = span.name
    override fun getKind() = span.kind.toSpanKind()
    override fun getSpanContext() = context
    override fun getParentSpanContext() = TODO()
    override fun getStatus() = span.status?.toStatusData()
    override fun getStartEpochNanos() = span.startTimeUnixNano
    override fun getAttributes(): Attributes = attributes
    override fun getEvents() = span.events.map { it.toEventData() }
    override fun getLinks() = span.links.map { it.toLinkData() }
    override fun getEndEpochNanos() = span.endTimeUnixNano
    override fun hasEnded() = span.endTimeUnixNano > 0
    override fun getTotalRecordedEvents() = span.events.size
    override fun getTotalRecordedLinks() = span.links.size
    override fun getTotalAttributeCount() = span.attributes.size
    @Deprecated("Deprecated in parent interface")
    override fun getInstrumentationLibraryInfo(): InstrumentationLibraryInfo = InstrumentationLibraryInfo.empty()
    override fun getResource(): Resource = Resource.create(resource.attributes.toAttributes())
}

private fun createContext(traceId: String, spanId: String, flags: Int?, state: String?): SpanContext {
    val traceFlags: TraceFlags = TraceFlags.fromByte(flags?.toByte() ?: 0)
    val traceState: TraceState = TraceState.builder().apply {
        // https://www.w3.org/TR/trace-context/#tracestate-header-field-values
        state?.split(",")?.map { it.trim() }?.forEach {
            put(it.substringBefore('='), it.substringAfter('='))
        }
    }.build()
    return SpanContext.create(traceId, spanId, traceFlags, traceState)
}

private fun Span.SpanKind.toSpanKind(): SpanKind = when (this) {
    Span.SpanKind.SPAN_KIND_UNSPECIFIED -> SpanKind.INTERNAL // assumption allowed by spec
    Span.SpanKind.SPAN_KIND_INTERNAL -> SpanKind.INTERNAL
    Span.SpanKind.SPAN_KIND_SERVER -> SpanKind.SERVER
    Span.SpanKind.SPAN_KIND_CLIENT -> SpanKind.CLIENT
    Span.SpanKind.SPAN_KIND_PRODUCER -> SpanKind.PRODUCER
    Span.SpanKind.SPAN_KIND_CONSUMER -> SpanKind.CONSUMER
}

private fun List<KeyValue>.toAttributes(): Attributes {
    val keyValues = this
    return Attributes.builder().apply {
        keyValues.forEach { put(it.key, it.value) }
    }.build()
}

private fun AttributesBuilder.put(key: String, value: AnyValue) {
    when {
        value.stringValue != null -> put(key, value.stringValue)
        value.intValue != null -> put(key, value.intValue)
        value.doubleValue != null -> put(key, value.doubleValue)
        value.boolValue != null -> put(key, value.boolValue)
        value.bytesValue != null -> put(key, value.bytesValue)
        value.arrayValue != null -> when {
            value.arrayValue.values.isEmpty() -> put(AttributeKey.stringArrayKey(key), emptyList())
            // we assume a homogeneous array because the API doesn't support heterogeneous arrays anyway
            value.arrayValue.values[0].stringValue != null -> put(AttributeKey.stringArrayKey(key), value.arrayValue.values.mapNotNull { it.stringValue })
            value.arrayValue.values[0].intValue != null -> put(AttributeKey.longArrayKey(key), value.arrayValue.values.mapNotNull { it.intValue })
            value.arrayValue.values[0].boolValue != null -> put(AttributeKey.booleanArrayKey(key), value.arrayValue.values.mapNotNull { it.boolValue })
            value.arrayValue.values[0].doubleValue != null -> put(AttributeKey.doubleArrayKey(key), value.arrayValue.values.mapNotNull { it.doubleValue })
            else -> error("Unsupported array element type for attribute $key: $value")
        }
        value.kvlistValue != null -> error("key-value pair list is not supported as attribute value")
    }
}

private fun Span.Link.toLinkData(): LinkData = LinkData.create(
    createContext(traceId, spanId, flags, traceState),
    attributes.toAttributes(),
    attributes.size,
)

private fun Span.Event.toEventData(): EventData = EventData.create(
    timeUnixNano,
    name,
    attributes.toAttributes(),
    attributes.size,
)

private fun Status.toStatusData(): StatusData = StatusData.create(code.toStatusCode(), message)

private fun Status.StatusCode.toStatusCode(): StatusCode = when (this) {
    Status.StatusCode.STATUS_CODE_UNSET -> StatusCode.UNSET
    Status.StatusCode.STATUS_CODE_OK -> StatusCode.OK
    Status.StatusCode.STATUS_CODE_ERROR -> StatusCode.ERROR
}
