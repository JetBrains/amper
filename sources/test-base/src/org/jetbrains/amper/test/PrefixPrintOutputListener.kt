/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test

import org.jetbrains.amper.processes.ProcessOutputListener


/**
 * A simple [ProcessOutputListener] that outputs stdout and stderr lines to the standard output of the current process
 * using [println], prepending a prefix of the form `[<name> <pid> <'out'|'err'>]`.
 */
@Suppress("ReplacePrintlnWithLogging") // these println are for test outputs and are OK here
class PrefixPrintOutputListener(
    private val cmdName: String,
    private val printErrToStdErr: Boolean = false,
) : ProcessOutputListener {

    override fun onStdoutLine(line: String, pid: Long) {
        println("[$cmdName $pid out] $line")
    }

    override fun onStderrLine(line: String, pid: Long) {
        val stream = if (printErrToStdErr) System.err else System.out
        stream.println("[$cmdName $pid err] $line")
    }
}
