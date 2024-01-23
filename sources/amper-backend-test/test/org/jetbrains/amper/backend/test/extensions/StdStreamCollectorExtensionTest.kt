/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.backend.test.extensions

import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.Test
import kotlin.test.assertEquals

class StdStreamCollectorExtensionTest {

    @RegisterExtension
    private val stdout: StdStreamCollectorExtension = StdoutCollectorExtension()
    @RegisterExtension
    private val stderr: StdStreamCollectorExtension = StderrCollectorExtension()

    @Test
    fun shouldStartEmpty() {
        assertEquals("", stdout.capturedText())
    }

    @Test
    fun shouldCaptureStdout() {
        assertEquals("", stdout.capturedText())
        print("hello")
        assertEquals("hello", stdout.capturedText())
        println(", stdout!")
        assertEquals("hello, stdout!$NL", stdout.capturedText())
        println("...and others")
        assertEquals("hello, stdout!$NL...and others$NL", stdout.capturedText())

        assertEquals(listOf("hello, stdout!", "...and others", ""), stdout.capturedText().lines())
    }

    @Test
    fun shouldClearCapturedOutput() {
        print("hello")
        assertEquals("hello", stdout.capturedText())
        stdout.clear()
        assertEquals("", stdout.capturedText())
    }

    @Test
    fun shouldCaptureStderr() {
        assertEquals("", stderr.capturedText())
        System.err.print("hello")
        assertEquals("hello", stderr.capturedText())
    }

    companion object {
        private val NL = System.lineSeparator()
    }
}
