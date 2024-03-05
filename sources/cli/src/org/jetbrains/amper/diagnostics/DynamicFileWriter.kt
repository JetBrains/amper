/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import org.tinylog.Level
import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.FileWriter
import org.tinylog.writers.Writer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.Volatile
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteExisting
import kotlin.io.path.pathString

class DynamicFileWriter(private val properties: Map<String, String>): Writer {
    @Volatile
    private var delegate: Writer? = null

    val level: Level?
        get() = properties["level"]?.let { Level.valueOf(it.uppercase()) }

    @Synchronized
    fun setFile(file: Path) {
        if (delegate != null) {
            error("Unable to set file name second time")
        }
        delegate = FileWriter(properties + mapOf("file" to file.absolutePathString()))
    }

    override fun getRequiredLogEntryValues(): MutableCollection<LogEntryValue> {
        val d = delegate
        if (d != null) {
            return d.requiredLogEntryValues
        }

        val tempFile = Files.createTempFile("log-conf", "")
        val tempWriter = FileWriter(properties + mapOf("file" to tempFile.pathString))
        try {
            return tempWriter.requiredLogEntryValues
        } finally {
            tempWriter.close()
            tempFile.deleteExisting()
        }
    }

    override fun write(logEntry: LogEntry?) {
        delegate?.write(logEntry)
    }

    override fun flush() {
        delegate?.flush()
    }

    override fun close() {
        delegate?.close()
    }
}
