/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.junit.listeners

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import org.junit.platform.commons.util.ExceptionUtils.readStackTrace
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

private const val configPropertyGroup = "org.jetbrains.amper.junit.listener.console"

private fun createDefaultTerminal(): Terminal =
    Terminal(
        ansiLevel = System.getProperty("$configPropertyGroup.ansiLevel")
            ?.let { AnsiLevel.valueOf(it) },
        hyperlinks = System.getProperty("$configPropertyGroup.ansiHyperlinks")
            ?.toBooleanStrictOrNull(),
    )

@Suppress("unused") // used by ServiceLoader
class ConsolePrintingTestExecutionListener(
    private val terminal: Terminal = createDefaultTerminal(),
) : TestExecutionListener {

    private val enabled = System.getProperty("$configPropertyGroup.enabled", "false").toBooleanStrict()

    private val ignoredRootContainers = mutableSetOf<UniqueId>()

    override fun testPlanExecutionStarted(testPlan: TestPlan) {
        // We don't want to print engine container names in the console.
        // Most will have no tests, and the one used in the module doesn't really bring useful information
        ignoredRootContainers.addAll(testPlan.roots.map { it.uniqueIdObject })
    }

    override fun executionSkipped(testIdentifier: TestIdentifier, reason: String?) {
        if (shouldIgnore(testIdentifier)) {
            return
        }
        printEvent(event = TestEvent.Skipped, testIdentifier)
        if (reason != null) {
            printlnMessage(TestEvent.Skipped.style, "Reason", reason)
        }
    }

    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (shouldIgnore(testIdentifier)) {
            return
        }
        val event = if (testIdentifier.isContainer) TestEvent.ContainerStarted else TestEvent.TestStarted
        printEvent(event = event, testIdentifier)
    }

    override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
        if (shouldIgnore(testIdentifier)) {
            return
        }
        val event = TestEvent.forResult(testExecutionResult)
        printEvent(event = event, testIdentifier)
        testExecutionResult.throwable.ifPresent { t ->
            printlnMessage(event.style, "Exception", readStackTrace(t))
        }
    }

    override fun reportingEntryPublished(testIdentifier: TestIdentifier, entry: ReportEntry) {
        if (shouldIgnore(testIdentifier)) {
            return
        }
        val (outputEntries, reportEntries) = entry.keyValuePairs.entries.partition {
            it.key in setOf(StreamingOutputKeys.STDOUT, StreamingOutputKeys.STDERR)
        }

        outputEntries.forEach {
            @Suppress("ReplacePrintlnWithLogging")
            when (it.key) {
                StreamingOutputKeys.STDOUT -> println(it.value)
                StreamingOutputKeys.STDERR -> System.err.println(it.value)
            }
        }

        if (reportEntries.isNotEmpty()) {
            printEvent(event = TestEvent.Reported, testIdentifier)
            reportEntries.forEach { (key, value) ->
                printlnMessage(TestEvent.Reported.style, key, value)
            }
        }
    }

    private fun shouldIgnore(testIdentifier: TestIdentifier): Boolean =
        !enabled || testIdentifier.uniqueIdObject in ignoredRootContainers

    private fun printEvent(event: TestEvent, testIdentifier: TestIdentifier) {
        println(event.style, "${bold(event.displayName).padEnd(columnWidth)} ${testIdentifier.displayName}")
    }

    private fun printlnMessage(style: TextStyle?, message: String, detail: String) {
        println(style, "$indent=> $message: ${detail.prependIndent(indent).trim()}")
    }

    private fun println(style: TextStyle?, message: String) {
        terminal.println(message.withStyle(style))
    }

    private fun String.withStyle(style: TextStyle?): String? = style?.invoke(this) ?: this

    companion object {
        private val columnWidth: Int = TestEvent.entries.maxOf { it.displayName.length }
        private val indent: String = " ".repeat(columnWidth + 2)
    }
}

private enum class TestEvent(val displayName: String, val style: TextStyle? = null) {
    ContainerStarted("Started", Theme.Default.info),
    TestStarted("Started"),
    Skipped("Skipped", Theme.Default.muted),
    Failed("Failed", Theme.Default.danger),
    Succeeded("Passed", Theme.Default.success),
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
