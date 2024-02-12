/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test.extensions

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.consumeAsFlow
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.ByteBuffer

fun StdoutCollectorExtension() = StdStreamCollectorExtension(System.out, System::setOut)

fun StderrCollectorExtension() = StdStreamCollectorExtension(System.err, System::setErr)

class StdStreamCollectorExtension(
    private val default: PrintStream,
    private val setStream: (PrintStream) -> Unit,
) : Extension, BeforeEachCallback, AfterEachCallback {

    private val tappedStream = TappedPrintStream(default)

    val lines: Flow<String> = tappedStream.channel.consumeAsFlow().cancellable()

    fun capturedText(): String = tappedStream.capturedText()

    override fun beforeEach(context: ExtensionContext?) {
        reset()
        setStream(tappedStream)
    }

    override fun afterEach(context: ExtensionContext?) {
        setStream(default)
        tappedStream.channel.close()
    }

    fun reset() {
        tappedStream.reset()
    }
}

private class TappedPrintStream(delegate: OutputStream) : PrintStream(delegate, false, Charsets.UTF_8) {
    private val captured = ByteArrayOutputStream()

    val channel: Channel<String> = Channel(CONFLATED)

    fun capturedText(): String = captured.toString(Charsets.UTF_8)

    private val capturedLine: StringBuilder = StringBuilder()

    override fun write(b: Int) {
        super.write(b)
        b.toByteArray().appendToStringBuilderOrSend()
        captured.write(b)
    }

    private fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(4).putInt(this).array()

    override fun write(b: ByteArray) {
        super.write(b)
        b.appendToStringBuilderOrSend()
        captured.write(b)
    }

    override fun write(buf: ByteArray, off: Int, len: Int) {
        super.write(buf, off, len)
        buf
            .slice(off until off + len)
            .filter { it.toInt() >= 0 }
            .toByteArray()
            .appendToStringBuilderOrSend()
        captured.write(buf, off, len)
    }

    fun reset() {
        captured.reset()
    }

    private fun ByteArray.appendToStringBuilderOrSend() {
        map { Char(it.toInt()) }.forEach {
            if (it == '\n') {
                channel.trySend(capturedLine.toString())
                capturedLine.clear()
            } else {
                capturedLine.append(it)
            }
        }
    }
}
