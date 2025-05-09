/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import com.github.ajalt.mordant.terminal.Terminal

/**
 * A [ProcessOutputListener] that writes the output through the Mordant [terminal].
 * This plays nice with the Mordant widgets we're using, such as task progress indicators.
 *
 * Using the raw stdout/stderr would interlace with the task progress widgets and break them.
 *
 * Note: the Mordant terminal is used in a way that respects the process's output (\t chars are not replaced with
 * a variable number of spaces, and ANSI codes are printed as-is without escaping).
 */
open class PrintToTerminalProcessOutputListener(private val terminal: Terminal) : ProcessOutputListener {
    override fun onStdoutLine(line: String, pid: Long) {
        // Using rawPrint instead of println because we don't want to modify the output of the process.
        // Terminal.println would replace \t with a variable number of spaces, and escape ANSI codes.
        // Note: we also don't use rawPrint(line) + println() because the 2 calls might interleave with other processes
        terminal.rawPrint("$line${System.lineSeparator()}")
    }

    override fun onStderrLine(line: String, pid: Long) {
        // Using rawPrint instead of println because we don't want to modify the output of the process.
        // Terminal.println would replace \t with a variable number of spaces, and escape ANSI codes.
        // Note: we also don't use rawPrint(line) + println() because the 2 calls might interleave with other processes
        terminal.rawPrint("$line${System.lineSeparator()}", stderr = true)
    }
}
