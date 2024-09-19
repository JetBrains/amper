/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import org.jetbrains.amper.core.AmperBuild
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.concurrent.thread
import kotlin.io.path.pathString

object TelemetryEnvironment {

    private val otlpLogger = Logger.getLogger(OtlpJsonLoggingSpanExporter::class.java.getName())

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
        val fileHandler = FileHandler(spansFile.pathString, true) // true to append, false to overwrite
        fileHandler.formatter = MessageOnlyFormatter
        otlpLogger.addHandler(fileHandler)
        otlpLogger.level = Level.ALL
    }

    fun setup() {
        // traces are not exported until we set the logs root directory
        otlpLogger.level = Level.OFF

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(OtlpJsonLoggingSpanExporter.create()).build())
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

private object MessageOnlyFormatter : Formatter() {
    // we have to wrap it in resourceSpans ourselves, apparently:
    // https://github.com/open-telemetry/opentelemetry-java/issues/6749
    override fun format(record: LogRecord?): String = """{"resourceSpans":[${formatMessage(record)}]}""" + "\n"
}
