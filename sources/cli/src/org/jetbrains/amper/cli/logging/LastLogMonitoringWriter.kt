/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.logging

import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.Writer
import kotlin.concurrent.Volatile
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class LastLogMonitoringWriter(@Suppress("UNUSED_PARAMETER") properties: Map<String, String>): Writer {
    override fun write(logEntry: LogEntry) {
        lastLogEntryTimeMark = TimeSource.Monotonic.markNow()
    }

    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> = emptyList()
    override fun flush() = Unit
    override fun close() = Unit

    companion object {
        @Volatile
        var lastLogEntryTimeMark: TimeMark = TimeSource.Monotonic.markNow()
    }
}
