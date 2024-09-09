/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.diagnostics.JaegerJsonSpanExporter
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

object TelemetryEnvironment {

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

    fun setup(logsRoot: AmperBuildLogsRoot) {
        // TODO: Implement some kind of background batch processing like in intellij

        val spansFile = logsRoot.path.resolve("jaeger-trace.json")

        val jaegerJsonSpanExporter = JaegerJsonSpanExporter(
            file = spansFile,
            serviceName = resource.getAttribute(AttributeKey.stringKey("service.name"))!!,
            serviceNamespace = resource.getAttribute(AttributeKey.stringKey("service.namespace"))!!,
            serviceVersion = resource.getAttribute(AttributeKey.stringKey("service.version"))!!,
        )

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(object : SpanExporter {
                override fun export(spans: Collection<SpanData>): CompletableResultCode {
                    runBlocking {
                        jaegerJsonSpanExporter.export(spans)
                    }
                    return CompletableResultCode.ofSuccess()
                }

                override fun flush(): CompletableResultCode {
                    runBlocking {
                        jaegerJsonSpanExporter.flush()
                    }
                    return CompletableResultCode.ofSuccess()
                }

                override fun shutdown(): CompletableResultCode {
                    runBlocking {
                        jaegerJsonSpanExporter.shutdown()
                    }
                    return CompletableResultCode.ofSuccess()
                }
            }))
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