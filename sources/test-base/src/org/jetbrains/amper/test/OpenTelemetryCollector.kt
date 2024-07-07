/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

internal object OpenTelemetryCollector {
    private val listeners = CopyOnWriteArrayList<Consumer<SpanData>>()

    init {
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(object : SpanProcessor {
                override fun onStart(p0: Context, p1: ReadWriteSpan) = Unit
                override fun isStartRequired(): Boolean = false
                override fun onEnd(span: ReadableSpan) {
                    val spanData = span.toSpanData()
                    listeners.forEach { it.accept(spanData) }
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

    fun addListener(listener: Consumer<SpanData>) {
        if (!listeners.add(listener)) {
            error("Listener already added")
        }
    }

    fun removeListener(listener: Consumer<SpanData>) {
        if (!listeners.remove(listener)) {
            error("Listener not found")
        }
    }
}
