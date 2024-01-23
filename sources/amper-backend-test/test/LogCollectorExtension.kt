/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.tinylog.core.LogEntry

class LogCollectorExtension : BeforeEachCallback, AfterEachCallback {
    private val collected = mutableListOf<LogEntry>()

    val entries: List<LogEntry>
        get() = synchronized(collected) { collected.toList() }

    fun reset() {
        synchronized(collected) {
            collected.clear()
        }
    }

    private val logListener = object : TestInterceptorWriter.Listener {
        override fun onLogEntry(logEntry: LogEntry): Unit = synchronized(collected) {
            collected.add(logEntry)
        }
    }

    override fun beforeEach(context: ExtensionContext?) {
        TestInterceptorWriter.addListener(logListener)
    }

    override fun afterEach(context: ExtensionContext?) {
        TestInterceptorWriter.removeListener(logListener)
    }
}