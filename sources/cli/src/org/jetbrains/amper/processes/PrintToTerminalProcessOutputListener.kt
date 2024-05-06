/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import com.github.ajalt.mordant.terminal.Terminal

class PrintToTerminalProcessOutputListener(private val terminal: Terminal) : ProcessOutputListener {
    override fun onStdoutLine(line: String) {
        terminal.println(line)
    }

    override fun onStderrLine(line: String) {
        terminal.println(line, stderr = true)
    }
}
