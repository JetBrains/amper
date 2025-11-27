/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.Locale
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Helper to capture the [System.`in`]/[System.err] output in a given context. See [capturing].
 */
object StandardStreamsCapture {
    private val outStream by lazy {
        InterceptingOutputStream(System.out).also { System.setOut(PrintStreamWithUnlockedAutoFlush(it)) }
    }
    private val errStream by lazy {
        InterceptingOutputStream(System.err).also { System.setErr(PrintStreamWithUnlockedAutoFlush(it)) }
    }

    /**
     * Enables the thread-local stdout/stderr interception.
     * Each line is reported in [onStdoutLine]/[onStderrLine] callback.
     */
    fun <R> capturing(
        onStdoutLine: (String) -> Unit,
        onStderrLine: (String) -> Unit,
        block: () -> R,
    ): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

        val streamToInterception = listOf(
            outStream to Interception(onStdoutLine),
            errStream to Interception(onStderrLine),
        )
        try {
            for ((stream, interception) in streamToInterception) {
                check(stream.currentInterception == null) { "capturingStandardStreams is not reentrant!" }
                stream.currentInterception = interception
            }

            return block()
        } finally {
            for ((stream, interception) in streamToInterception) {
                stream.currentInterception = null
                // Flush the remaining stuff if any
                flushAllRemaining(interception)
            }
        }
    }

    private fun flushAllRemaining(interception: Interception) {
        val remainingLines = interception.buffer.toString().lines()
        remainingLines.forEachIndexed { i, line ->
            if (i != remainingLines.lastIndex || line.isNotEmpty()) {
                interception.onLine(line)
            }
        }
        for (childInterception in interception.children) {
            flushAllRemaining(childInterception)
        }
    }

    private class Interception(
        val onLine: (String) -> Unit,
    ) {
        // This doesn't have to be thread safe because it only is accessed from the single thread
        // that the instance is *local* to.
        var buffer = ByteArrayOutputStream()

        // Interception objects, local to child threads
        var children = mutableListOf<Interception>()
    }

    private class InterceptingOutputStream(
        output: OutputStream,
    ) : FilterOutputStream(output) {

        // inheritable is needed here to also intercept output from child threads
        private val interceptionHolder = object : InheritableThreadLocal<Interception>() {
            override fun childValue(parentValue: Interception?): Interception? {
                // Ensure each thread has its own buffer to write to
                return parentValue?.let {
                    val child = Interception(onLine = it.onLine)
                    // childValue is called in the parent thread, so there are no races here
                    it.children += child
                    child
                }
            }
        }

        var currentInterception: Interception?
            get() = interceptionHolder.get()
            set(value) = interceptionHolder.set(value)

        override fun write(b: Int) {
            currentInterception?.buffer?.write(b)
                ?: super.write(b)
        }

        override fun write(b: ByteArray?) {
            currentInterception?.buffer?.write(b)
                ?: super.write(b)
        }

        override fun write(b: ByteArray?, off: Int, len: Int) {
            currentInterception?.buffer?.write(b, off, len)
                ?: super.write(b, off, len)
        }

        override fun flush() {
            val interception = currentInterception
            if (interception == null) {
                super.flush()
                return
            }

            val lines = interception.buffer.toString().lines()

            try {
                // Temporarily unset the interception so that `onStdoutLine` implementations that write
                // to the intercepted stream again do not get intercepted indefinitely
                currentInterception = null
                lines.dropLast(1).forEach { line ->
                    interception.onLine(line)
                }
            } finally {
                // Restore the interception
                currentInterception = interception
            }

            // Reset the buffer
            interception.buffer = ByteArrayOutputStream()

            val lastLine = lines.last()
            if (lastLine.isNotEmpty()) {
                // Write back the last incomplete line - we can't flush it, we only accept complete lines.
                interception.buffer.writeBytes(lastLine.toByteArray())
            }
        }
    }
}

/**
 * A [PrintStream] with "auto-flush" but [flush] is not called in a synchronized context.
 * This may be required to avoid deadlocks in the output interception mechanism.
 *
 * Deadlock case:
 * ```
 * Task thread:      PrintStream.flush (locks A) -> SLF4JLogger -> Terminal (waits for B)
 * Animation thread: Terminal (locks B) -> PrintStream.flush (waits for A)
 * ```
 */
private class PrintStreamWithUnlockedAutoFlush(
    private val output: OutputStream,
) : PrintStream(/*we delegate manually*/nullOutputStream(), /*we do the autoFlush manually*/false) {
    override fun flush() {
        output.flush()
    }

    override fun write(b: Int) = output.write(b)
    override fun write(buf: ByteArray?, off: Int, len: Int) = output.write(buf, off, len)
    override fun write(buf: ByteArray?) = output.write(buf)

    // region override potentially flushing methods

    override fun print(s: CharArray) = super.print(s).also { if ('\n' in s) flush() }
    override fun print(s: String?) = super.print(s).also { if (s != null && '\n' in s) flush() }
    override fun print(obj: Any?) = print(obj.toString())

    override fun println() = super.println().also { flush() }
    override fun println(x: Boolean) = super.println(x).also { flush() }
    override fun println(x: Char) = super.println(x).also { flush() }
    override fun println(x: Int) = super.println(x).also { flush() }
    override fun println(x: Long) = super.println(x).also { flush() }
    override fun println(x: Float) = super.println(x).also { flush() }
    override fun println(x: Double) = super.println(x).also { flush() }
    override fun println(x: CharArray) = super.println(x).also { flush() }
    override fun println(x: String?) = super.println(x).also { flush() }
    override fun println(x: Any?) = super.println(x).also { flush() }

    override fun format(format: String, vararg args: Any?) = apply { print(String.format(format, *args)) }
    override fun format(l: Locale, format: String, vararg args: Any?) = apply { print(String.format(l, format, *args)) }

    // endregion
}