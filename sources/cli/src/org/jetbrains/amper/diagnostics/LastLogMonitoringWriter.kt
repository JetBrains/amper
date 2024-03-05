/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.Writer
import java.time.Instant
import kotlin.concurrent.Volatile

class LastLogMonitoringWriter: Writer {
    override fun write(logEntry: LogEntry) {
        lastLogEntryTimestamp = Instant.now()
    }

    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> = emptyList()
    override fun flush() = Unit
    override fun close() = Unit

    companion object {
        @Volatile
        var lastLogEntryTimestamp: Instant = Instant.now()
    }
}