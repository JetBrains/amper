/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCollectorTest {
    @Test
    fun spans() = runTestWithCollector {
        spanBuilder("span1").setAttribute("key1", "value1").use {
            coroutineScope {
                launch(Dispatchers.IO) {
                    spanBuilder("span2").setAttribute("key2", "value2").use {
                        it.addEvent("x")
                    }
                }
            }
        }

        assertEquals(2, spans.size)
        val (span1, span2) = spans.sortedBy { it.name }

        assertEquals("span1", span1.name)
        assertEquals("key1=value1", span1.attributes.asMap().map { "${it.key}=${it.value}" }.joinToString(" "))

        assertEquals("span2", span2.name)
        assertEquals("key2=value2", span2.attributes.asMap().map { "${it.key}=${it.value}" }.joinToString(" "))

        assertEquals(span1.spanId, span2.parentSpanId)
    }

    @Test
    fun javaUtilLogger() = runTestWithCollector {
        val julLogger = java.util.logging.Logger.getLogger("test-jul-logger")
        julLogger.info("jul info")
        julLogger.warning("jul warning")

        val entries = logEntries.sortedBy { it.message }
        assertEquals(2, entries.size)
        assertEquals("jul info", entries[0].message)
        assertEquals("jul warning", entries[1].message)
    }

    @Test
    fun slf4jLogger() = runTestWithCollector {
        val slf4jLogger = org.slf4j.LoggerFactory.getLogger("test-slf4j-logger")
        slf4jLogger.info("slf4j info")
        slf4jLogger.warn("slf4j warning")

        val entries = logEntries.sortedBy { it.message }
        assertEquals(2, entries.size)
        assertEquals("slf4j info", entries[0].message)
        assertEquals("slf4j warning", entries[1].message)
    }

    @Test
    fun tinylogLogger() = runTestWithCollector {
        coroutineScope {
            launch(Dispatchers.IO) {
                org.tinylog.Logger.info("tinylog info" as Any)
                org.tinylog.Logger.warn("tinylog warn" as Any)
            }
        }

        val entries = logEntries.sortedBy { it.message }
        assertEquals(2, entries.size)
        assertEquals("tinylog info", entries[0].message)
        assertEquals("tinylog warn", entries[1].message)
    }

    @Test
    fun terminal() = runTestWithCollector {
        terminal.println("terminal stdout")
        terminal.println("terminal stderr", stderr = true)

        assertEquals("terminal stdout\n", terminalRecorder.stdout())
        assertEquals("terminal stderr\n", terminalRecorder.stderr())
    }
}
