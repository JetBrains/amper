/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler
import io.opentelemetry.exporter.logging.otlp.internal.writer.JsonWriter
import io.opentelemetry.exporter.logging.otlp.internal.writer.StreamJsonWriter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.diagnostics.rmi.LoopbackClientSocketFactory
import org.jetbrains.amper.diagnostics.rmi.SpanExporterService
import org.jetbrains.amper.diagnostics.rmi.toSerializable
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.file.Path
import java.rmi.RemoteException
import java.rmi.registry.LocateRegistry
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.io.path.outputStream

object TelemetryEnvironment {

    private val deferredSpansFile = DeferredFile()

    private val resource: Resource = Resource.create(
        Attributes.builder()
            .put(AttributeKey.stringKey("service.name"), "Amper")
            .put(AttributeKey.stringKey("service.version"), AmperBuild.mavenVersion)
            .put(AttributeKey.stringKey("service.namespace"), "amper")
            .put(AttributeKey.stringKey("os.type"), System.getProperty("os.name"))
            .put(AttributeKey.stringKey("os.version"), System.getProperty("os.version").lowercase())
            .put(AttributeKey.stringKey("host.arch"), System.getProperty("os.arch"))
            .put(AttributeKey.stringKey("service.instance.id"), DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
            .build()
    )

    fun setLogsRootDirectory(amperBuildLogsRoot: AmperBuildLogsRoot) {
        val spansFile = amperBuildLogsRoot.path.resolve("opentelemetry_traces.jsonl")
        deferredSpansFile.setFile(spansFile)
    }

    fun setup() {
        val exporter = OtlpStdoutSpanExporter(deferredSpansFile.outputStream)
        val rmiSpanExporterServiceName = System.getenv(SpanExporterService.NAME_ENV_VAR)
        val compositeSpanExporter = if (rmiSpanExporterServiceName != null) {
            SpanExporter.composite(exporter, RmiSpanExporter(rmiSpanExporterServiceName))
        } else exporter

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(compositeSpanExporter).build())
            .setResource(resource)
            .build()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()
        GlobalOpenTelemetry.set(openTelemetry)

        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            try {
                openTelemetry.close()
            } catch (t: Throwable) {
                LoggerFactory.getLogger(javaClass).error("Exception on shutdown: ${t.message}", t)
            }
        })
    }
}

/**
 * Represents a file whose location is initially unknown.
 *
 * Writing to [outputStream] initially writes to an in-memory buffer.
 * Once [setFile] is called, an asynchronous copy of the in-memory buffer is started, and continues to run indefinitely.
 * Data can still be written to [outputStream] concurrently and goes to the file through the buffer.
 */
private class DeferredFile {
    private val pipeEntrance = PipedOutputStream()
    private val pipeExit = PipedInputStream(pipeEntrance)

    /**
     * The stream to write the data to, so it eventually gets to the file.
     */
    val outputStream = BufferedOutputStream(pipeEntrance) // this buffer should be sufficient while waiting for the file

    private val fileSet = AtomicBoolean(false)

    @OptIn(DelicateCoroutinesApi::class) // we do want a coroutine that lives until Amper terminates, to flush to the file
    fun setFile(path: Path) {
        if (fileSet.compareAndSet(false, true)) {
            GlobalScope.launch(CoroutineName("telemetry-pipe") + Dispatchers.IO) {
                pipeExit.use { input ->
                    path.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } else {
            error("File path has already been set.")
        }
    }
}

// TODO remove this. This is a simplified copy of the real OtlpStdoutSpanExporter that we temporarily use while waiting
//  for the fix of this issue to be released: https://github.com/open-telemetry/opentelemetry-java/issues/6836
private class OtlpStdoutSpanExporter(private val outputStream: OutputStream) : SpanExporter {
    private val isShutdown = AtomicBoolean()
    private val jsonWriter: JsonWriter = StreamJsonWriter(outputStream, "spans")

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        if (isShutdown.get()) {
            return CompletableResultCode.ofFailure()
        }
        val request = TraceRequestMarshaler.create(spans)
        val result = jsonWriter.write(request)
        jsonWriter.flush()
        outputStream.write('\n'.code) // this is the bit that the built-in OtlpStdoutSpanExporter is missing
        return result
    }

    override fun flush(): CompletableResultCode {
        return jsonWriter.flush()
    }

    override fun shutdown(): CompletableResultCode {
        if (isShutdown.compareAndSet(false, true)) {
            jsonWriter.close()
        }
        return CompletableResultCode.ofSuccess()
    }
}

private class RmiSpanExporter(
    serviceName: String,
) : SpanExporter {
    private val registry = LocateRegistry.getRegistry(
        LoopbackClientSocketFactory.hostName, SpanExporterService.PORT, LoopbackClientSocketFactory)
    private val service = registry.lookup(serviceName) as SpanExporterService

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        return try {
            service.export(spans.map(SpanData::toSerializable))
            CompletableResultCode.ofSuccess()
        } catch (e: RemoteException) {
            e.printStackTrace()
            CompletableResultCode.ofFailure()
        }
    }

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()
    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}