/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.telemetry

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.telemetry.useWithoutCoroutines

// Note: the tracer name is not used in traces, and should just be linked to the instrumentation library itself.
// It is different from the service name, which does appear in traces.
// We use the package here, not as the service name, but as a way to refer to this library.
private val tracer: Tracer
    get() = GlobalOpenTelemetry.getTracer("org.jetbrains.amper.telemetry")

/**
 * Creates a [SpanBuilder] to configure a new span with the given [spanName].
 *
 * The span can be created and run using [SpanBuilder.use], or [SpanBuilder.useWithoutCoroutines] if we can't use
 * coroutines and won't use coroutines down the line.
 */
fun spanBuilder(spanName: String): SpanBuilder = tracer.spanBuilder(spanName)