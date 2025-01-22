/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.junit.listeners

import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import org.junit.platform.commons.util.ExceptionUtils.readStackTrace
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier

private enum class TestEvent(val displayName: String, val style: TextStyle? = null) {
    ContainerStarted("Started", Theme.Default.info),
    TestStarted("Started"),
    Skipped("Skipped", Theme.Default.muted),
    Failed("Failed", Theme.Default.danger),
    Succeeded("Success", Theme.Default.success),
    Reported("Reported"),
    EntryReported("Reported");

    companion object {
        fun forResult(result: TestExecutionResult): TestEvent = when (result.status) {
            TestExecutionResult.Status.SUCCESSFUL -> Succeeded
            TestExecutionResult.Status.ABORTED -> Skipped
            TestExecutionResult.Status.FAILED -> Failed
        }
    }
}

class ConsolePrintingTestExecutionListener(private val out: Terminal) : TestExecutionListener {

    override fun executionSkipped(testIdentifier: TestIdentifier, reason: String?) {
        printEvent(event = TestEvent.Skipped, testIdentifier)
        if (reason != null) {
            printlnMessage(TestEvent.Skipped.style, "Reason", reason)
        }
    }

    override fun executionStarted(testIdentifier: TestIdentifier) {
        val event = if (testIdentifier.isContainer) TestEvent.ContainerStarted else TestEvent.TestStarted
        printEvent(event = event, testIdentifier)
    }

    override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
        val event = TestEvent.forResult(testExecutionResult)
        printEvent(event = event, testIdentifier)
        testExecutionResult.throwable.ifPresent { t ->
            printlnMessage(event.style, "Exception", readStackTrace(t))
        }
    }

    override fun reportingEntryPublished(testIdentifier: TestIdentifier, entry: ReportEntry) {
        printEvent(event = TestEvent.Reported, testIdentifier)
        printlnMessage(TestEvent.Reported.style, "Reported values", entry.toString())
    }

    private fun printEvent(event: TestEvent, testIdentifier: TestIdentifier) {
        println(event.style, "${event.displayName.padEnd(columnWidth)} ${testIdentifier.displayName}")
    }

    private fun printlnMessage(style: TextStyle?, message: String, detail: String) {
        println(style, "$indent=> $message: ${detail.prependIndent(indent).trim()}")
    }

    private fun println(style: TextStyle?, message: String) {
        out.println(message.withStyle(style))
    }

    private fun String.withStyle(style: TextStyle?): String? = style?.invoke(this) ?: this

    companion object {
        private val columnWidth: Int = TestEvent.entries.maxOf { it.displayName.length }
        private val indent: String = " ".repeat(columnWidth + 2)
    }
}
