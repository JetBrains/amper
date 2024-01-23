/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test.extensions

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext

class OpenTelemetryCollectorExtension: Extension, BeforeEachCallback, AfterEachCallback {
    private val collected = mutableListOf<SpanData>()

    val spans: List<SpanData>
        get() = synchronized(collected) { collected.toList() }

    fun reset() {
        synchronized(collected) {
            collected.clear()
        }
    }

    override fun beforeEach(context: ExtensionContext?) {
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(object : SpanProcessor {
                override fun onStart(p0: Context, p1: ReadWriteSpan) = Unit
                override fun isStartRequired(): Boolean = false
                override fun onEnd(span: ReadableSpan) {
                    synchronized(collected) {
                        collected.add(span.toSpanData())
                    }
                }
                override fun isEndRequired(): Boolean = true
            })
            .build()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()
        GlobalOpenTelemetry.resetForTest()
        GlobalOpenTelemetry.set(openTelemetry)
    }

    override fun afterEach(context: ExtensionContext?) {
        GlobalOpenTelemetry.resetForTest()
        GlobalOpenTelemetry.set(OpenTelemetry.noop())
    }
}