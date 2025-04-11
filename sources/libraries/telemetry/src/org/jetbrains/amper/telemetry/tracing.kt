/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.telemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.use

/**
 * Starts the span from this [SpanBuilder] and runs the given [operation] under this span, in contexts that don't know
 * about coroutines.
 *
 * The span is immediately started, and automatically ended when [operation] completes.
 * The successful or exceptional completion of [operation] is recorded in the span.
 *
 * **Warning:** this is exclusively for places that can't use coroutines and thus will also not call suspend functions
 * in the given [operation]. If you need to call suspend functions, use [use] instead.
 *
 * **Important:** the OTel context is set in a [ThreadLocal], so we must not create coroutines inside the given
 * [operation] unless we manually transfer the context using [asContextElement].
 */
// This is not 'inline' on purpose, to forbid the use of suspend functions inside (it would break OTel parent-child links)
/* NOT inline */ fun <T> SpanBuilder.useWithoutCoroutines(operation: (Span) -> T): T =
    startSpan().useWithoutScopeAndRecordCompletion { span ->
        // makeCurrent() modifies a ThreadLocal, but not the current coroutine context
        span.makeCurrent().use {
            operation(span)
        }
    }

/**
 * Starts the span from this [SpanBuilder] and runs the given [operation] under this span, with OTel context
 * propagation through coroutine contexts.
 *
 * The span is immediately started, and automatically ended when [operation] completes.
 * The successful or exceptional completion of [operation] is recorded in the span.
 *
 * The running span is registered as [ThreadContextElement] in the nested context, so the usual [ThreadLocal]-based OTel
 * context is available, and any OTel usage in [operation] can see it.
 *
 * Note: any direct modification to the OTel's [ThreadLocal] context (e.g. via [SpanBuilder.useWithoutCoroutines]) will
 * not be propagated to the coroutine context automatically. We need to keep using [use] everytime down the line of
 * coroutines calls, unless coroutines won't be used at all inside [operation].
 */
suspend inline fun <T> SpanBuilder.use(crossinline operation: suspend CoroutineScope.(Span) -> T): T {
    val span = startSpan()
    return withContext(span.asContextElement()) {
        span.useWithoutScopeAndRecordCompletion {
            operation(span)
        }
    }
}

/**
 * Runs the given [operation] with this [Span] as argument, and sets the appropriate status on completion.
 *
 * This function doesn't handle the OTel context/scope - it must be handled separately.
 */
@PublishedApi // to be able to mark it at least 'internal' if not private, and use from a public function
internal inline fun <T> Span.useWithoutScopeAndRecordCompletion(operation: (Span) -> T): T {
    try {
        return operation(this)
    } catch (e: CancellationException) {
        setAttribute("cancelled", true)
        setStatus(StatusCode.ERROR)
        throw e
    } catch (e: Throwable) {
        recordException(e)
        setStatus(StatusCode.ERROR)
        throw e
    } finally {
        end()
    }
}
