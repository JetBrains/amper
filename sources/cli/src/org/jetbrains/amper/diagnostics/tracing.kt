/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.withContext
import org.jetbrains.amper.frontend.PotatoModule
import kotlin.coroutines.cancellation.CancellationException

private const val AMPER_SCOPE_NAME = "amper"

val tracer: Tracer
    get() = GlobalOpenTelemetry.getTracer(AMPER_SCOPE_NAME)

val meter: Meter
    get() = GlobalOpenTelemetry.getMeter(AMPER_SCOPE_NAME)

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

fun SpanBuilder.setAmperModule(module: PotatoModule) = setAttribute("amper-module", module.userReadableName)

fun SpanBuilder.setListAttribute(key: String, list: List<String>) = setAttribute(AttributeKey.stringArrayKey(key), list)

/**
 * Returns the value of the attribute [key], or throws [NoSuchElementException] if no such attribute exists in this span.
 */
fun <T> SpanData.getAttribute(key: AttributeKey<T>) = attributes[key]
    ?: throw NoSuchElementException("attribute '$key' not found in span '$name'")
