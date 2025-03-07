/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.trace.IdGenerator
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jetbrains.amper.concurrency.withReentrantLock
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories

// based on community/platform/diagnostic/telemetry.exporters/src/JaegerJsonSpanExporter.kt

// https://github.com/jaegertracing/jaeger-ui/issues/381
class JaegerJsonSpanExporter(
  file: Path,
  serviceName: String,
  serviceVersion: String? = null,
  serviceNamespace: String? = null,
) : AsyncSpanExporter {
  private val fileChannel: FileChannel
  private val writer: JsonGenerator

  private val lock = Mutex()

  init {
    file.parent.createDirectories()

    fileChannel = FileChannel.open(file, EnumSet.of(StandardOpenOption.CREATE,
                                                    StandardOpenOption.WRITE,
                                                    StandardOpenOption.TRUNCATE_EXISTING))

    writer = JsonFactory().createGenerator(Channels.newOutputStream(fileChannel))
      .configure(com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_TARGET, true)
      // Channels.newOutputStream doesn't implement flush, but just to be sure
      .configure(com.fasterxml.jackson.core.JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false)

    beginWriter(w = writer, serviceName = serviceName, serviceVersion = serviceVersion, serviceNamespace = serviceNamespace)
  }

  @Suppress("DuplicatedCode")
  override suspend fun export(spans: Collection<SpanData>) {
    lock.withReentrantLock {
      for (span in spans) {
        writer.writeStartObject()
        writer.writeStringField("traceID", span.traceId)
        writer.writeStringField("spanID", span.spanId)
        writer.writeStringField("operationName", span.name)
        writer.writeStringField("processID", "p1")
        writer.writeNumberField("startTime", TimeUnit.NANOSECONDS.toMicros(span.startEpochNanos))
        writer.writeNumberField("duration", TimeUnit.NANOSECONDS.toMicros(span.endEpochNanos - span.startEpochNanos))
        val parentContext = span.parentSpanContext
        val hasError = span.status.statusCode == StatusData.error().statusCode

        val attributes = span.attributes
        if (!attributes.isEmpty || hasError) {
          writer.writeArrayFieldStart("tags")
          if (hasError) {
            writer.writeStartObject()
            writer.writeStringField("key", "otel.status_code")
            writer.writeStringField("type", "string")
            writer.writeStringField("value", "ERROR")
            writer.writeEndObject()
            writer.writeStartObject()
            writer.writeStringField("key", "error")
            writer.writeStringField("type", "bool")
            writer.writeBooleanField("value", true)
            writer.writeEndObject()
          }
          writeAttributesAsJson(writer, attributes)
          writer.writeEndArray()
        }

        val events = span.events
        if (!events.isEmpty()) {
          writer.writeArrayFieldStart("logs")
          for (event in events) {
            writer.writeStartObject()
            writer.writeNumberField("timestamp", TimeUnit.NANOSECONDS.toMicros(event.epochNanos))
            writer.writeArrayFieldStart("fields")

            // event name as event attribute
            writer.writeStartObject()
            writer.writeStringField("key", "event")
            writer.writeStringField("type", "string")
            writer.writeStringField("value", event.name)
            writer.writeEndObject()
            writeAttributesAsJson(writer, event.attributes)
            writer.writeEndArray()
            writer.writeEndObject()
          }
          writer.writeEndArray()
        }

        if (parentContext.isValid) {
          writer.writeArrayFieldStart("references")
          writer.writeStartObject()
          writer.writeStringField("refType", "CHILD_OF")
          writer.writeStringField("traceID", parentContext.traceId)
          writer.writeStringField("spanID", parentContext.spanId)
          writer.writeEndObject()
          writer.writeEndArray()
        }
        writer.writeEndObject()
      }
    }
  }

  override suspend fun shutdown() {
    lock.withReentrantLock {
      withContext(Dispatchers.IO) {
        fileChannel.use {
          closeJsonFile(writer)
        }
      }
    }
  }

  override suspend fun flush() {
    lock.withReentrantLock {
      // if shutdown was already invoked OR nothing has been written to the temp file
      if (writer.isClosed) {
        return@withReentrantLock
      }

      writer.flush()
      fileChannel.write(ByteBuffer.wrap(jsonEnd))
      fileChannel.force(false)
      fileChannel.position(fileChannel.position() - jsonEnd.size)
    }
  }
}

private fun beginWriter(w: JsonGenerator,
                        serviceName: String,
                        serviceVersion: String?,
                        serviceNamespace: String?) {
  w.writeStartObject()
  w.writeArrayFieldStart("data")
  w.writeStartObject()
  w.writeStringField("traceID", IdGenerator.random().generateTraceId())

  // process info
  w.writeObjectFieldStart("processes")
  w.writeObjectFieldStart("p1")
  w.writeStringField("serviceName", serviceName)

  w.writeArrayFieldStart("tags")

  w.writeStartObject()
  w.writeStringField("key", "time")
  w.writeStringField("type", "string")
  w.writeStringField("value", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME))
  w.writeEndObject()

  if (serviceVersion != null) {
    writeStringTag("service.version", serviceVersion, w)
  }
  if (serviceNamespace != null) {
    writeStringTag("service.namespace", serviceNamespace, w)
  }

  w.writeEndArray()

  w.writeEndObject()
  w.writeEndObject()
  w.writeArrayFieldStart("spans")
}

private fun writeStringTag(name: String, value: String, w: JsonGenerator) {
  w.writeStartObject()
  w.writeStringField("key", name)
  w.writeStringField("type", "string")
  w.writeStringField("value", value)
  w.writeEndObject()
}

private fun writeAttributesAsJson(w: JsonGenerator, attributes: Attributes) {
  attributes.forEach { k, v ->
    w.writeStartObject()
    w.writeStringField("key", k.key)
    w.writeStringField("type", k.type.name.lowercase())
    if (v is Iterable<*>) {
      w.writeArrayFieldStart("value")
      for (item in v) {
        w.writeString(item as String)
      }
      w.writeEndArray()
    }
    else {
      w.writeStringField("value", v.toString())
    }
    w.writeEndObject()
  }
}


private fun closeJsonFile(jsonGenerator: JsonGenerator) {
  // close spans
  jsonGenerator.writeEndArray()
  // close data item object
  jsonGenerator.writeEndObject()
  // close data
  jsonGenerator.writeEndArray()
  // close the root object
  jsonGenerator.writeEndObject()
  jsonGenerator.close()
}

private val jsonEnd = "]}]}".encodeToByteArray()
