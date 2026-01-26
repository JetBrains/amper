/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.telemetry

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.logging.otlp.internal.traces.OtlpStdoutSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.io.OutputStream
import kotlin.concurrent.thread

object TelemetrySetup {

    fun createOpenTelemetry(jsonLinesOutputStream: OutputStream, resource: Resource): OpenTelemetrySdk {
        val exporter = OtlpStdoutSpanExporter.builder()
            .setOutput(jsonLinesOutputStream)
            .setWrapperJsonObject(true)
            .build()
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .setResource(resource)
            .build()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setPropagators(
                // propagators are needed to share the OTEL context between the main Amper process and child processes started from it
                ContextPropagators.create(W3CTraceContextPropagator.getInstance())
            )
            .setTracerProvider(tracerProvider)
            .build()
        return openTelemetry
    }

    fun closeTelemetryOnShutdown(openTelemetry: OpenTelemetrySdk, handleShutdownError: (Throwable) -> Unit) {
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            try {
                openTelemetry.close()
            } catch (t: Throwable) {
                handleShutdownError(t)
            }
        })
    }
}