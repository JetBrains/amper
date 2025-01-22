/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.logging.otlp.internal.traces.OtlpStdoutSpanExporter
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
import org.jetbrains.amper.telemetry.toSerializable
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Path
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
        val exporter = OtlpStdoutSpanExporter.builder()
            .setOutput(deferredSpansFile.outputStream)
            .setWrapperJsonObject(true)
            .build()
        val spanListenerPort = System.getProperty("amper.internal.testing.otlp.port")?.toIntOrNull()
        val compositeSpanExporter = if (spanListenerPort != null) {
            SpanExporter.composite(exporter, SocketSpanExporter(port = spanListenerPort))
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

private class SocketSpanExporter(
    port: Int,
) : SpanExporter {
    private val socket = Socket(InetAddress.getLoopbackAddress(), port)
    private val outputStream = ObjectOutputStream(socket.getOutputStream().buffered())

    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        return try {
            spans.forEach { span ->
                outputStream.writeObject(span.toSerializable())
            }
            CompletableResultCode.ofSuccess()
        } catch (e: IOException) {
            e.printStackTrace()
            CompletableResultCode.ofFailure()
        }
    }

    override fun flush(): CompletableResultCode {
        outputStream.flush()
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        outputStream.flush()
        socket.close()
        return CompletableResultCode.ofSuccess()
    }
}