/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.trace.data.SpanData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.slf4j.MDC
import org.tinylog.core.LogEntry
import org.tinylog.jul.JulTinylogBridge
import java.util.UUID
import java.util.function.Consumer
import kotlin.time.Duration

// TODO assert log errors

class TestCollector(val backgroundScope: CoroutineScope) {
    private val collectedSpans = mutableListOf<SpanData>()
    private fun addSpan(spanData: SpanData) = synchronized(collectedSpans) { collectedSpans.add(spanData) }
    val spans: List<SpanData>
        get() = synchronized(collectedSpans) { collectedSpans.toList() }
    fun clearSpans() = synchronized(collectedSpans) { collectedSpans.clear() }

    private val collectedLogEntries = mutableListOf<LogEntry>()
    private fun addLogEntry(logEntry: LogEntry) = synchronized(collectedLogEntries) { collectedLogEntries.add(logEntry) }
    val logEntries: List<LogEntry>
        get() = synchronized(collectedLogEntries) { collectedLogEntries.toList() }
    fun clearLogEntries() = synchronized(collectedLogEntries) { collectedLogEntries.clear() }

    val terminalRecorder = TerminalRecorder()
    val terminal: Terminal = Terminal(terminalRecorder)
    fun cleatTerminalRecording() = terminalRecorder.clearOutput()

    companion object {
        private const val MDC_KEY = "test-collector"
        fun runTestWithCollector(timeout: Duration = Duration.INFINITE, block: suspend TestCollector.() -> Unit) {
            val id = UUID.randomUUID().toString()

            runTest(timeout = timeout) {
                val testCollector = TestCollector(backgroundScope = backgroundScope)

                MDC.putCloseable(MDC_KEY, id).use {
                    withContext(Context.current().with(KEY, CurrentCollector(id)).asContextElement() + MDCContext()) {
                        val listener = Consumer<SpanData> {
                            if (Context.current().get(KEY)?.id == id) {
                                testCollector.addSpan(it)
                            }
                        }
                        val logListener = object : TestInterceptorWriter.Listener {
                            override fun onLogEntry(logEntry: LogEntry) {
                                if (logEntry.context[MDC_KEY] == id) {
                                    testCollector.addLogEntry(logEntry)
                                }
                            }
                        }

                        OpenTelemetryCollector.addListener(listener)
                        TestInterceptorWriter.addListener(logListener)
                        try {
                            block(testCollector)
                        } finally {
                            OpenTelemetryCollector.removeListener(listener)
                            TestInterceptorWriter.removeListener(logListener)
                        }
                    }
                }
            }
        }

        init {
            JulTinylogBridge.activate()
        }

        private data class CurrentCollector(val id: String)
        private val KEY = ContextKey.named<CurrentCollector>("opentelemetry-test-collector")
    }
}
