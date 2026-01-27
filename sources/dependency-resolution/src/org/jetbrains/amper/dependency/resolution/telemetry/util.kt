/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.telemetry

import io.opentelemetry.api.OpenTelemetry
import org.jetbrains.amper.dependency.resolution.Settings
import org.jetbrains.amper.dependency.resolution.SpanBuilderSource
import org.slf4j.LoggerFactory

/**
 * Use this span builder for blocks that should be reported on every execution
 * (log level for logger "DR.telemetry" is set to INFO)
 */
internal val Settings.infoSpanBuilder
    get() = if (telemetryLogger.isInfoEnabled) spanBuilder else NoopTelemetry.noopSpanBuilder

/**
 * Use this span builder for blocks that should be reported for DEBUG execution only
 * (log level for logger "DR.telemetry" is set to DEBUG)
 */
internal val Settings.debugSpanBuilder
    get() = if (telemetryLogger.isDebugEnabled) spanBuilder else NoopTelemetry.noopSpanBuilder

private val telemetryLogger = LoggerFactory.getLogger("org.jetbrains.amper.dependency.resolution.telemetry.utilKt")

private val Settings.spanBuilder: SpanBuilderSource
    get() = {
        openTelemetry
            .getTracer("org.jetbrains.amper.dr")
            .spanBuilder(it)
    }

private object NoopTelemetry {
    val noopSpanTracer = OpenTelemetry.noop().getTracer("org.jetbrains.amper.dr")
    val noopSpanBuilder: SpanBuilderSource = {
        noopSpanTracer.spanBuilder(it)
    }
}