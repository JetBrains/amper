/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.processes

import java.io.PrintStream

interface ProcessOutputListener {
    fun onStdoutLine(line: String, pid: Long)
    fun onStderrLine(line: String, pid: Long)
    fun onProcessTerminated(exitCode: Int, pid: Long) {}

    /**
     * A [ProcessOutputListener] that ignores all output.
     */
    object NOOP : ProcessOutputListener {
        override fun onStdoutLine(line: String, pid: Long) = Unit
        override fun onStderrLine(line: String, pid: Long) = Unit
    }

    /**
     * A [ProcessOutputListener] that forwards the process streams to the given [stdout] and [stderr] output streams.
     */
    class Streaming(
        val stdout: PrintStream = System.out,
        val stderr: PrintStream = System.err,
    ) : ProcessOutputListener {
        override fun onStdoutLine(line: String, pid: Long) = stdout.println(line)
        override fun onStderrLine(line: String, pid: Long) = stderr.println(line)
    }

    /**
     * A [ProcessOutputListener] that captures the outputs in memory and allows to access them after the process exits.
     */
    class InMemoryCapture : ProcessOutputListener {
        private val stdoutBuffer: StringBuilder = StringBuilder()
        private val stderrBuffer: StringBuilder = StringBuilder()

        val stdout: String get() = stdoutBuffer.toString()
        val stderr: String get() = stderrBuffer.toString()

        override fun onStdoutLine(line: String, pid: Long) {
            stdoutBuffer.appendLine(line)
        }

        override fun onStderrLine(line: String, pid: Long) {
            stderrBuffer.appendLine(line)
        }
    }

    /**
     * Combines this listener with [other] into a new listener that notifies both.
     */
    operator fun plus(other: ProcessOutputListener): ProcessOutputListener =
        if (this is CompositeProcessOutputListener) {
            CompositeProcessOutputListener(listeners + other)
        } else {
            CompositeProcessOutputListener(listOf(this, other))
        }
}

private class CompositeProcessOutputListener(val listeners: List<ProcessOutputListener>) : ProcessOutputListener {
    override fun onStdoutLine(line: String, pid: Long) {
        listeners.forEach { it.onStdoutLine(line, pid) }
    }

    override fun onStderrLine(line: String, pid: Long) {
        listeners.forEach { it.onStderrLine(line, pid) }
    }

    override fun onProcessTerminated(exitCode: Int, pid: Long) {
        listeners.forEach { it.onProcessTerminated(exitCode, pid) }
    }
}
