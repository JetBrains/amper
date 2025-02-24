/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.test.processes

import org.jetbrains.amper.processes.ProcessOutputListener
import org.junit.jupiter.api.TestReporter

class TestReporterProcessOutputListener(
    private val cmdName: String,
    private val testReporter: TestReporter,
) : ProcessOutputListener {

    override fun onStdoutLine(line: String, pid: Long) {
        testReporter.publishEntry(AmperJUnitReporterKeys.STDOUT, "[$cmdName $pid out] $line\n")
    }

    override fun onStderrLine(line: String, pid: Long) {
        testReporter.publishEntry(AmperJUnitReporterKeys.STDERR, "[$cmdName $pid err] $line\n")
    }
}
