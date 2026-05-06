/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.compilation

import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.io.IOException
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.relativeToOrSelf
import kotlin.io.path.useLines

private val internalLogger = LoggerFactory.getLogger(TerminalCompilerMessageRenderer::class.java)

/**
 * Renderer that formats Kotlin compiler messages for the terminal output by adding the highlighted snippet of code
 * to the error message.
 */
internal class TerminalCompilerMessageRenderer(
    private val terminal: Terminal,
    private val projectRoot: Path,
    private val module: AmperModule,
) : CompilerMessageRenderer {
    override fun render(
        severity: CompilerMessageRenderer.Severity,
        message: String,
        location: CompilerMessageRenderer.SourceLocation?,
    ): String {
        val locationStr = location?.let {
            val relativePath = Path(it.path).relativeToOrSelf(projectRoot)
            "$relativePath:${it.line}:${it.column} (${module.userReadableName})"
        }

        val severityStyle = when (severity) {
            CompilerMessageRenderer.Severity.ERROR -> terminal.theme.danger
            CompilerMessageRenderer.Severity.WARNING -> terminal.theme.warning
            CompilerMessageRenderer.Severity.INFO -> terminal.theme.info
            // Do not render debug messages
            CompilerMessageRenderer.Severity.DEBUG -> return "$locationStr: $message"
        }
        val muted = terminal.theme.muted
        val snippet = location?.let { resolveSnippet(it) }
        val locationWithHyperlink = locationStr?.let { TextStyles.hyperlink("file://${location.path}")(it) }

        terminal.println(buildString {
            if (location != null && snippet != null) {
                val maxLineNo = location.line + snippet.size - 1
                val gutterWidth = maxLineNo.toString().length
                val borderPrefix = " ".repeat(gutterWidth + 1)
                val isMultiLine = snippet.size > 1
                append(muted("$borderPrefix╭─ "))
                append(severityStyle(TextStyles.bold("${severity.name}: ")))
                append(TextStyles.bold(message))
                appendLine()

                locationWithHyperlink?.let {
                    append(muted("$borderPrefix│ → "))
                    append(it)
                    appendLine()
                }

                appendLine(muted("$borderPrefix│"))

                if (isMultiLine) {
                    append(muted("$borderPrefix│ "))
                    append(severityStyle(buildTopPointer(location, snippet.first())))
                    appendLine()
                }

                snippet.forEachIndexed { i, line ->
                    val lineNo = (location.line + i).toString().padStart(gutterWidth)
                    append(muted("$lineNo │ "))
                    append(highlightRange(line, location, i, snippet.size, severityStyle))
                    appendLine()
                }

                append(muted("$borderPrefix│ "))
                append(severityStyle(buildBottomPointer(location, isMultiLine)))
                appendLine()

                append(muted("$borderPrefix╰─"))
            } else {
                append(severityStyle(TextStyles.bold("${severity.name}: ")))
                append(TextStyles.bold(message))
                locationWithHyperlink?.let {
                    appendLine()
                    append("  - $it")
                }
            }
        }, stderr = severity == CompilerMessageRenderer.Severity.ERROR)
        return "$locationStr: $message"
    }

    private fun resolveSnippet(location: CompilerMessageRenderer.SourceLocation): List<String>? {
        if (location.lineEnd <= location.line) {
            return location.lineContent?.let { listOf(it) }
        }
        val lines = try {
            Path(location.path).useLines { linesSequence ->
                linesSequence
                    // Lines in location are 1-based
                    .drop(location.line - 1)
                    .take(location.lineEnd - location.line + 1)
                    .toList()
            }
        } catch (e: IOException) {
            internalLogger.error(
                "Failed to read file snippet for location: ${location.path}:${location.line}-${location.lineEnd}", e
            )
            return null
        }
        return lines.takeIf { it.isNotEmpty() } ?: location.lineContent?.let { listOf(it) }
    }

    private fun buildTopPointer(
        location: CompilerMessageRenderer.SourceLocation,
        firstLine: String,
    ): String {
        val padding = location.column - 1
        val length = firstLine.length - padding
        return " ".repeat(padding) + "⌄".repeat(length)
    }

    private fun buildBottomPointer(
        location: CompilerMessageRenderer.SourceLocation,
        isMultiLine: Boolean,
    ): String = if (isMultiLine) {
        val length = (location.columnEnd - 1).coerceAtLeast(1)
        "⌃".repeat(length)
    } else {
        val padding = location.column - 1
        val length = if (location.columnEnd > location.column) {
            location.columnEnd - location.column
        } else {
            1
        }
        " ".repeat(padding) + "⌃".repeat(length)
    }

    private fun highlightRange(
        line: String,
        location: CompilerMessageRenderer.SourceLocation,
        lineIndex: Int,
        totalLines: Int,
        style: TextStyle,
    ): String {
        val start: Int
        val end: Int
        when {
            totalLines == 1 -> {
                start = (location.column - 1).coerceIn(0, line.length)
                end = if (location.columnEnd > location.column) {
                    (location.columnEnd - 1).coerceIn(start, line.length)
                } else {
                    (start + 1).coerceAtMost(line.length)
                }
            }
            lineIndex == 0 -> {
                start = (location.column - 1).coerceIn(0, line.length)
                end = line.length
            }
            lineIndex == totalLines - 1 -> {
                start = 0
                end = if (location.columnEnd > 0) {
                    (location.columnEnd - 1).coerceIn(0, line.length)
                } else {
                    line.length
                }
            }
            else -> {
                start = 0
                end = line.length
            }
        }
        return line.substring(0, start) + style(line.substring(start, end)) + line.substring(end)
    }
}