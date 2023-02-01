/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import io.opentelemetry.api.GlobalOpenTelemetry.get
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val AMPER_SCOPE_NAME = "amper"

//this is a single instance that they recommend to use in OTel docs
val opentelemetry: OpenTelemetry by lazy { get() }

val tracer: Tracer
    get() = opentelemetry.getTracer(AMPER_SCOPE_NAME)

val meter: Meter
    get() = opentelemetry.getMeter(AMPER_SCOPE_NAME)

inline fun <T> Span.use(operation: (Span) -> T): T {
    try {
        return operation(this)
    }
    catch (e: CancellationException) {
        // TODO record span as canceled?
        throw e
    }
    catch (e: Throwable) {
        recordException(e)
        setStatus(StatusCode.ERROR)
        throw e
    }
    finally {
        end()
    }
}

suspend inline fun <T> SpanBuilder.useWithScope(crossinline operation: suspend (Span) -> T): T {
    val span = startSpan()
    return withContext(Context.current().with(span).asContextElement()) {
        span.use {
            operation(span)
        }
    }
}

fun spanBuilder(spanName: String): SpanBuilder = tracer.spanBuilder(spanName)
