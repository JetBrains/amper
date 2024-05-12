/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.diagnostics.DoNotLogToTerminalCookie.REPEL_TERMINAL_LOGGING_MDC_NAME
import org.tinylog.Level
import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.AbstractFormatPatternWriter
import kotlin.concurrent.Volatile

class DynamicLevelConsoleWriter(properties: Map<String, String>): AbstractFormatPatternWriter(properties) {
    @Volatile
    private var minimumLevel: Level = Level.INFO

    @Volatile
    private var terminal: Terminal? = null

    @Synchronized
    fun setLevel(level: Level) {
        minimumLevel = level
    }

    @Synchronized
    fun setTerminal(terminal: Terminal) {
        this.terminal = terminal
    }

    override fun write(logEntry: LogEntry) {
        if (logEntry.level.ordinal >= minimumLevel.ordinal) {
            if (!logEntry.context.containsKey(REPEL_TERMINAL_LOGGING_MDC_NAME)) {
                val isError = logEntry.level.ordinal >= Level.ERROR.ordinal
                terminal?.println(render(logEntry).trim(), stderr = isError)
            }
        }
    }

    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> {
        val logEntryValues = super.getRequiredLogEntryValues()
        logEntryValues.add(LogEntryValue.LEVEL)
        logEntryValues.add(LogEntryValue.CONTEXT)
        return logEntryValues
    }

    override fun flush() = Unit
    override fun close() = Unit
}
