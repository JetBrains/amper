/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.jetbrains.amper.processes.ProcessOutputListener

/**
 * A simple [ProcessOutputListener] that outputs stdout and stderr lines to the standard output of the current process
 * using [println].
 */
@Suppress("ReplacePrintlnWithLogging") // these println are for test outputs and are OK here
object SimplePrintOutputListener : ProcessOutputListener {

    override fun onStdoutLine(line: String, pid: Long) {
        println(line)
    }

    override fun onStderrLine(line: String, pid: Long) {
        println(line)
    }
}
