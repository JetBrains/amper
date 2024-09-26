/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private const val AMPER_SCOPE_NAME = "amper"

private val tracer: Tracer
    get() = GlobalOpenTelemetry.getTracer(AMPER_SCOPE_NAME)

private val meter: Meter
    get() = GlobalOpenTelemetry.getMeter(AMPER_SCOPE_NAME)

/**
 * Don't use this method; use [use] instead.
 */
@PublishedApi // to be able to mark it at least 'internal' if not private, and use from a public function
internal inline fun <T> Span.useWithoutActiveScope(operation: (Span) -> T): T {
    try {
        return operation(this)
    }
    catch (e: CancellationException) {
        setAttribute("cancelled", true)
        setStatus(StatusCode.ERROR)
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

/**
 * Runs the given [operation] under this span, in contexts that don't know about coroutines.
 *
 * The span is immediately started, and automatically ended when [operation] completes.
 *
 * **Important:** the OTel context is set in a [ThreadLocal], so we must not create coroutines inside the given
 * [operation] unless we manually transfer the context using [asContextElement].
 */
// This is not 'inline' on purpose, to forbid the use of suspend functions inside (it would break OTel parent-child links)
/* NOT inline */ fun <T> SpanBuilder.use(operation: (Span) -> T): T = startSpan().useWithoutActiveScope { span ->
    // makeCurrent() modifies a ThreadLocal, but not the current coroutine context
    span.makeCurrent().use {
        operation(span)
    }
}

/**
 * Runs the given [operation] under this span, with proper context propagation.
 *
 * The span is immediately started, and automatically ended when [operation] completes.
 *
 * The running span is properly registered as [ThreadContextElement] in the nested context, so the usual
 * [ThreadLocal]-based OTel context is available, and any OTel usage in [operation] can see it.
 *
 * Note: any direct modification to the OTel's [ThreadLocal] context (e.g. via [SpanBuilder.use]) will not
 * be propagated to the coroutine context automatically. We need to keep using [useWithScope] everytime down the line
 * of coroutines calls, unless coroutines won't be used at all.
 */
suspend inline fun <T> SpanBuilder.useWithScope(crossinline operation: suspend CoroutineScope.(Span) -> T): T {
    val span = startSpan()
    return withContext(span.asContextElement()) {
        span.useWithoutActiveScope {
            operation(span)
        }
    }
}

fun spanBuilder(spanName: String): SpanBuilder = tracer.spanBuilder(spanName)
