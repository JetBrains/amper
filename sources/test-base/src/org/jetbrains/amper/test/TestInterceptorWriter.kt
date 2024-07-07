/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.AbstractWriter
import java.util.concurrent.CopyOnWriteArrayList

class TestInterceptorWriter(properties: MutableMap<String, String>) : AbstractWriter(properties) {
    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> =
        LogEntryValue.entries.toList()

    override fun write(logEntry: LogEntry) {
        listeners.forEach { it.onLogEntry(logEntry) }
    }

    override fun flush() {}

    override fun close() {}

    companion object {
        private var listeners: CopyOnWriteArrayList<Listener> = CopyOnWriteArrayList()

        fun addListener(listener: Listener) {
            if (!listeners.add(listener)) {
                error("Listener already added")
            }
        }

        fun removeListener(listener: Listener) {
            if (!listeners.remove(listener)) {
                error("Listener not found")
            }
        }
    }

    interface Listener {
        fun onLogEntry(logEntry: LogEntry)
    }
}
