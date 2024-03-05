/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import org.tinylog.Level
import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.ConsoleWriter
import org.tinylog.writers.Writer
import kotlin.concurrent.Volatile

class DynamicLevelConsoleWriter(properties: Map<String, String>): Writer {
    private val delegate = ConsoleWriter(properties)

    @Volatile
    private var minimumLevel: Level = Level.INFO

    @Synchronized
    fun setLevel(level: Level) {
        minimumLevel = level
    }

    override fun write(logEntry: LogEntry) {
        if (logEntry.level.ordinal >= minimumLevel.ordinal) {
            delegate.write(logEntry)
        }
    }

    override fun getRequiredLogEntryValues(): MutableCollection<LogEntryValue> = delegate.requiredLogEntryValues
    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}
