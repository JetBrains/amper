/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.logs

import org.slf4j.event.Level
import java.nio.file.Path
import kotlin.io.path.useLines
import kotlin.text.get

class LogEntry(val level: Level, val message: String)

/**
 * Reads the logs from this log file.
 */
fun Path.readLogs(): List<LogEntry> = useLines { lines ->
    lateinit var currentLevel: Level
    val currentMessage = StringBuilder()
    val logs = mutableListOf<LogEntry>()
    lines
        .map { parseLine(it) }
        .dropWhile { it !is LogFileLine.LogStart } // drop banner lines, go to the first real log
        .forEach {
            when (it) {
                is LogFileLine.Plain -> currentMessage.appendLine(it.line)
                is LogFileLine.LogStart -> {
                    if (currentMessage.isNotEmpty()) {
                        logs.add(LogEntry(currentLevel, currentMessage.toString()))
                        currentMessage.clear()
                    }
                    currentLevel = it.level
                    currentMessage.appendLine(it.message)
                }
            }
        }
    if (currentMessage.isNotEmpty()) {
        logs.add(LogEntry(currentLevel, currentMessage.toString()))
    }
    logs
}

private sealed interface LogFileLine {
    data class Plain(val line: String) : LogFileLine
    data class LogStart(
        val level: Level,
        val amperTaskName: String?,
        val className: String,
        val message: String,
    ) : LogFileLine
}

// See how it's defined for files in sources/cli/resources/tinylog.properties
private val logLineRegex = Regex("""(?<time>\d{2}:\d{2}.\d{3}+)\s+(?<level>[A-Z]+)\s+((?<amperTaskName>:\S+)\s+)?(?<class>(?!:)\S+)\s+(?<message>.*)""")

private fun parseLine(line: String): LogFileLine {
    val match = logLineRegex.matchEntire(line) ?: return LogFileLine.Plain(line)
    val level = match.groups["level"]?.value ?: error("mandatory 'level' group is missing")
    return LogFileLine.LogStart(
        level = Level.valueOf(level),
        amperTaskName = match.groups["amperTaskName"]?.value,
        className = match.groups["class"]?.value ?: error("mandatory 'class' group is missing"),
        message = match.groups["message"]?.value ?: error("mandatory 'message' group is missing"),
    )
}
