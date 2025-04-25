/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.junit.listeners

import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap

/**
 * A [PrintStream] based on a [ThreadAwareEavesdroppingOutputStream].
 */
internal class ThreadAwareEavesdroppingPrintStream<K> private constructor(
    private val outputStream: ThreadAwareEavesdroppingOutputStream<K>,
) : PrintStream(outputStream, true) {

    /**
     * Creates a [PrintStream] that transparently watches the given [original] print stream, buffers the output
     * independently for each value of the given [threadLocalKey], and reports full lines to [onLinePrinted],
     * attributed to each key.
     *
     * If [allowPartialLineFlush] is enabled, every [flush] will result in [onLinePrinted] being called, even if a line
     * is not finished (e.g. [print] has been called with no line ending). This might not be desirable if
     * [onLinePrinted] is used to print more output to the same underlying stream (because the output will be mingled
     * within a single line).
     * If disabled, only complete lines (with a line terminator) will be sent to `onLinePrinted`, which means it will
     * only be called when the original stream just printed a line terminator too. That is, unless [forceFlush] is
     * called, in which case even partial lines are flushed.
     */
    constructor(
        original: PrintStream,
        threadLocalKey: ThreadLocal<K>,
        forwardToOriginalStream: Boolean,
        allowPartialLineFlush: Boolean = false,
        onLinePrinted: (key: K, line: String) -> Unit,
    ) : this(ThreadAwareEavesdroppingOutputStream(
        original = original,
        threadLocalKey = threadLocalKey,
        forwardToOriginalStream = forwardToOriginalStream,
        allowPartialLineFlush = allowPartialLineFlush,
        onLinePrinted = onLinePrinted,
    ))

    /**
     * Flushes even partial lines (for instance at the end of a test), disregarding the `allowPartialLineFlush` config.
     */
    fun forceFlush() {
        outputStream.forceFlush()
    }
}

/**
 * An [OutputStream] that transparently wraps another [OutputStream], remembers the output independently for each value
 * of the given [threadLocalKey], and reports full lines to [onLinePrinted], attributed to each key.
 */
internal class ThreadAwareEavesdroppingOutputStream<K>(
    private val original: OutputStream,
    private val threadLocalKey: ThreadLocal<K>,
    private val forwardToOriginalStream: Boolean,
    private val allowPartialLineFlush: Boolean,
    private val onLinePrinted: (key: K, line: String) -> Unit,
) : FilterOutputStream(original) {

    private val threadBuffers = ConcurrentHashMap<K, StringBuffer>() // not StringBuilder because we want thread safety

    private fun getThreadBuffer(): StringBuffer? {
        val key = threadLocalKey.get() ?: return null
        return threadBuffers.computeIfAbsent(key) { StringBuffer() }
    }

    override fun write(buf: ByteArray, off: Int, len: Int) {
        if (forwardToOriginalStream) {
            original.write(buf, off, len)
        }
        getThreadBuffer()?.append(String(buf, off, len))
    }

    override fun write(b: Int) {
        if (forwardToOriginalStream) {
            original.write(b)
        }
        getThreadBuffer()?.append(b.toChar().toString())
    }

    override fun flush() {
        if (forwardToOriginalStream) {
            original.flush()
        }
        val buffer = getThreadBuffer() ?: return
        if (allowPartialLineFlush || buffer.endsWith("\n")) {
            sendAndClearBuffer()
        }
    }

    /**
     * Flushes even partial lines (for instance at the end of a test), disregarding the `allowPartialLineFlush` config.
     */
    fun forceFlush() {
        if (forwardToOriginalStream) {
            original.flush()
        }
        sendAndClearBuffer()
    }

    private fun sendAndClearBuffer() {
        val buffer = getThreadBuffer() ?: return
        val text = buffer.toString()
        if (text.isEmpty()) return
        onLinePrinted(threadLocalKey.get(), text)
        buffer.setLength(0)
    }
}
