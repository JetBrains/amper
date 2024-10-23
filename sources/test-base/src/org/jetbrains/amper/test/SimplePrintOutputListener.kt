/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.jetbrains.amper.processes.ProcessOutputListener

/**
 * A simple [ProcessOutputListener] that outputs both stdout and stderr lines to the standard output of the current
 * process, with optional prefixes for each stream.
 */
@Suppress("ReplacePrintlnWithLogging") // this println is for test outputs and are OK here
class SimplePrintOutputListener(
    val stdoutPrefix: String = "",
    val stderrPrefix: String = "",
) : ProcessOutputListener {

    override fun onStdoutLine(line: String) {
        println("$stdoutPrefix$line")
    }

    override fun onStderrLine(line: String) {
        println("$stderrPrefix$line")
    }
}
