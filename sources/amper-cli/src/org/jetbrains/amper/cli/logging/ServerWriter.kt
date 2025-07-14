/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.logging

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.Writer

private const val bufferCapacity = 1024

const val useKey = "use"
const val useServerValue = "server"
const val sessionIdKey = "sessionId"

class ServerWriter(properties: Map<String, String>): Writer {

    data class SessionedLogEntry(val uuid: String, val entry: LogEntry)

    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> = emptyList()

    override fun write(p0: LogEntry) {
        if (p0.context[useKey] != useServerValue) return
        val sessionId = p0.context[sessionIdKey] ?: return
        sharedFlow.tryEmit(SessionedLogEntry(uuid = sessionId, entry = p0))
    }

    override fun flush() {
    }

    override fun close() {
    }

    companion object {
        private val sharedFlow = MutableSharedFlow<SessionedLogEntry>(
            replay = 0,
            extraBufferCapacity = bufferCapacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        val logFlow: SharedFlow<SessionedLogEntry> = sharedFlow
    }
}
