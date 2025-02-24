/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.processes

import org.junit.jupiter.api.TestReporter
import java.io.OutputStream

fun TestReporter.out(linePrefix: String = ""): OutputStream =
    TestReporterOutputStream(this, AmperJUnitReporterKeys.STDOUT, linePrefix)

fun TestReporter.err(linePrefix: String = ""): OutputStream =
    TestReporterOutputStream(this, AmperJUnitReporterKeys.STDERR, linePrefix)

// We buffer lines for multiple reasons:
// 1. we can't publish blank entries (so a single \n would fail)
// 2. we want to support prefixes
private class TestReporterOutputStream(
    private val testReporter: TestReporter,
    private val entryKey: String,
    private val linePrefix: String,
) : OutputStream() {

    private val buffer = StringBuilder(linePrefix)

    override fun write(buf: ByteArray, off: Int, len: Int) {
        append(String(buf, off, len))
    }

    override fun write(b: Int) {
        append(b.toChar().toString())
    }

    private fun append(text: String) {
        val withPrefixedLines = if (linePrefix.isNotEmpty() && text.isNotEmpty()) text.prependIndent(linePrefix) else text
        synchronized(buffer) {
            buffer.append(withPrefixedLines)
            if (buffer.endsWith("\n")) {
                flush()
            }
        }
    }

    override fun flush() {
        synchronized(buffer) {
            if (!buffer.endsWith('\n')) {
                return
            }
            testReporter.publishEntry(entryKey, buffer.toString())
            buffer.setLength(0)
            buffer.append(linePrefix)
        }
    }
}
