/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeType
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.TraceStateBuilder
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.LinkData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import java.io.Serializable
import java.util.function.BiConsumer

fun SpanData.toSerializable(): SpanData {
    return SerializableSpanData(this)
}

private class SerializableSpanData(
    data: SpanData,
) : SpanData, Serializable {
    private val name: String = data.name
    private val kind: SpanKind = data.kind
    private val spanContext: SpanContext = SSpanContext(data.spanContext)
    private val parentSpanContext: SpanContext = SSpanContext(data.parentSpanContext)
    private val status: StatusData = SStatusData(data.status)
    private val startEpochNanos: Long = data.startEpochNanos
    private val attributes: Attributes = SAttributes(data.attributes)
    private val events: List<EventData> = data.events.map(::SEventData)
    private val links: List<LinkData> = data.links.map(::SLinkData)
    private val endEpochNanos = data.endEpochNanos
    private val hasEnded = data.hasEnded()
    private val resourceAttributes: Attributes = SAttributes(data.resource.attributes)
    private val resourceSchemaUrl: String? = data.resource.schemaUrl

    override fun getName() = name
    override fun getKind() = kind
    override fun getSpanContext() = spanContext
    override fun getParentSpanContext() = parentSpanContext
    override fun getStatus() = status
    override fun getStartEpochNanos() = startEpochNanos
    override fun getAttributes() = attributes
    override fun getEvents() = events
    override fun getLinks() = links
    override fun getEndEpochNanos() = endEpochNanos
    override fun hasEnded() = hasEnded
    override fun getTotalRecordedEvents() = events.size
    override fun getTotalRecordedLinks() = links.size
    override fun getTotalAttributeCount() = attributes.size()
    override fun getInstrumentationLibraryInfo(): InstrumentationLibraryInfo = InstrumentationLibraryInfo.empty()
    override fun getResource(): Resource = Resource.create(resourceAttributes, resourceSchemaUrl)

    private class SLinkData(
        data: LinkData,
    ) : LinkData, Serializable {
        private val spanContext: SpanContext = SSpanContext(data.spanContext)
        private val attributes: Attributes = SAttributes(data.attributes)

        override fun getSpanContext() = spanContext
        override fun getAttributes() = attributes
        override fun getTotalAttributeCount() = attributes.size()
    }

    private class SEventData(
        data: EventData,
    ) : EventData, Serializable {
        private val name: String = data.name
        private val attributes: Attributes = SAttributes(data.attributes)
        private val epochNanos: Long = data.epochNanos

        override fun getName() = name
        override fun getAttributes() = attributes
        override fun getEpochNanos() = epochNanos
        override fun getTotalAttributeCount() = attributes.size()
    }

    private class SAttributes(
        data: Attributes,
    ) : Attributes, Serializable {
        private val list: List<Pair<AttributeKeyProto, Any>> = data.asMap().map { (k, v) -> AttributeKeyProto(k) to v }
        private val map: Map<AttributeKey<*>, Any> by UnsafeTransientLazy {
            // UnsafeTransientLazy is needed because the hashCodes will change with the deserialization and the map
            //  will become invalid
            list.associateBy(
                keySelector = { it.first.asAttributeKey() },
                valueTransform = { it.second },
            )
        }

        private class AttributeKeyProto(
            data: AttributeKey<*>,
        ) : Serializable {
            private val key: String = data.key
            private val type: AttributeType = data.type

            fun asAttributeKey(): AttributeKey<*> = when (type) {
                AttributeType.STRING -> AttributeKey.stringKey(key)
                AttributeType.BOOLEAN -> AttributeKey.booleanKey(key)
                AttributeType.LONG -> AttributeKey.longKey(key)
                AttributeType.DOUBLE -> AttributeKey.doubleKey(key)
                AttributeType.STRING_ARRAY -> AttributeKey.stringArrayKey(key)
                AttributeType.BOOLEAN_ARRAY -> AttributeKey.booleanArrayKey(key)
                AttributeType.LONG_ARRAY -> AttributeKey.longArrayKey(key)
                AttributeType.DOUBLE_ARRAY -> AttributeKey.doubleArrayKey(key)
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> get(key: AttributeKey<T>) = map[key] as T?
        override fun forEach(consumer: BiConsumer<in AttributeKey<*>, in Any>) = map.forEach(consumer)
        override fun size(): Int = list.size
        override fun isEmpty(): Boolean = list.isEmpty()
        override fun asMap(): Map<AttributeKey<*>, Any> = map
        override fun toBuilder(): AttributesBuilder = TODO()
    }

    private class SStatusData (
        data: StatusData,
    ): StatusData, Serializable {
        private val statusCode: StatusCode = data.statusCode
        private val description: String = data.description

        override fun getStatusCode() = statusCode
        override fun getDescription() = description
    }

    private class STraceState(
        data: TraceState,
    ) : TraceState, Serializable {
        private val map: Map<String, String> = data.asMap().toMap()

        override fun get(key: String): String? = map[key]
        override fun size(): Int = map.size
        override fun isEmpty(): Boolean = map.isEmpty()
        override fun forEach(consumer: BiConsumer<String, String>) = map.forEach(consumer)
        override fun asMap(): Map<String, String> = map
        override fun toBuilder(): TraceStateBuilder = TraceState.builder().apply {
            map.forEach { put(it.key, it.value) }
        }
    }

    private class SSpanContext(
        data: SpanContext,
    ) : SpanContext, Serializable {
        private val traceId: String = data.traceId
        private val spanId: String = data.spanId
        private val traceFlags: Byte = data.traceFlags.asByte()
        private val traceState: TraceState = STraceState(data.traceState)
        private val remote: Boolean = data.isRemote

        override fun getTraceId() = traceId
        override fun getSpanId() = spanId
        override fun getTraceFlags(): TraceFlags = TraceFlags.fromByte(traceFlags)
        override fun getTraceState() = traceState
        override fun isRemote() = remote
    }
}

private class UnsafeTransientLazy<out T : Any>(
    private val initializer: () -> T,
) : Lazy<T>, Serializable {
    @Transient
    private var _value: T? = null

    override val value: T
        get() = _value ?: initializer().also { _value = it }

    override fun isInitialized(): Boolean = _value != null
}
