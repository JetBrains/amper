/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test.extensions

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream

fun StdoutCollectorExtension() = StdStreamCollectorExtension(System.out, System::setOut)

fun StderrCollectorExtension() = StdStreamCollectorExtension(System.err, System::setErr)

class StdStreamCollectorExtension(
    private val default: PrintStream,
    private val setStream: (PrintStream) -> Unit,
) : BeforeEachCallback, AfterEachCallback {

    private val tappedStream = TappedPrintStream(default)

    fun capturedText(): String = tappedStream.capturedText()

    override fun beforeEach(context: ExtensionContext?) {
        reset()
        setStream(tappedStream)
    }

    override fun afterEach(context: ExtensionContext?) {
        setStream(default)
    }

    fun reset() {
        tappedStream.reset()
    }
}

private class TappedPrintStream(delegate: OutputStream) : PrintStream(delegate, false, Charsets.UTF_8) {
    private val captured = ByteArrayOutputStream()

    fun capturedText(): String = captured.toString(Charsets.UTF_8)

    override fun write(b: Int) {
        super.write(b)
        captured.write(b)
    }

    override fun write(b: ByteArray) {
        super.write(b)
        captured.write(b)
    }

    override fun write(buf: ByteArray, off: Int, len: Int) {
        super.write(buf, off, len)
        captured.write(buf, off, len)
    }

    fun reset() {
        captured.reset()
    }
}
