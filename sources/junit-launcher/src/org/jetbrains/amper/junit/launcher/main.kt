/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.junit.launcher

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.junit.listeners.ConsolePrintingTestExecutionListener
import org.jetbrains.amper.junit.listeners.TeamCityMessagesTestExecutionListener
import org.junit.platform.engine.discovery.ClassNameFilter
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.listeners.TestExecutionSummary
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener
import java.io.File
import java.io.PrintWriter
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) = MainCommand().main(args)

private fun OptionWithValues<String?, String, String>.classpath() = convert { cp ->
    cp.split(File.pathSeparator).map { Path(it) }
}

class MainCommand : CliktCommand() {

    private val formats by option("--format", help = "Test execution listeners to enable")
        .enum<OutputFormat>(ignoreCase = true)
        .multiple()

    private val ansiLevel by option("--ansi-level", help = "The level of support for ANSI color codes to use (by default, it's automatically detected from the console)")
        .enum<AnsiLevel>(ignoreCase = true)

    private val hyperlinks by option("--ansi-hyperlinks", help = "Whether to allow ANSI hyperlinks codes in the output (by default, it's automatically detected from the console)")
        .enum<HyperlinkSupport>(ignoreCase = true)
        .default(HyperlinkSupport.Auto)

    private val reportsDir by option("--reports-dir")
        .path(mustExist = false, canBeFile = false, canBeDir = true)

    private val testRuntimeClasspath by option("--test-runtime-classpath", help = "The runtime classpath for the test execution")
        .classpath()
        .required()

    private val testDiscoveryClasspath by option("--test-discovery-classpath", help = "The classpath to scan for test discovery")
        .classpath()
        .required() // TODO review this

    private val selectMethods by option("--select-method", help = "Select tests by method").multiple()
    private val selectClass by option("--select-class", help = "Select tests by method").multiple()
    private val includeClasses by option("--include-classes", help = "Include tests by class name").multiple()
    private val excludeClasses by option("--exclude-classes", help = "Exclude tests by class name").multiple()

    @Suppress("ReplacePrintlnWithLogging")
    override fun run() {
        val testExecutionListeners = createTestExecutionListeners()
        val summary = runTests(testExecutionListeners)

        println() // to separate from the general test output

        printSummary(summary = summary)

        val nTestsFailed = summary.testsFailedCount
        if (nTestsFailed > 0) {
            System.err.println("$nTestsFailed test(s) failed")
            exitProcess(1)
        }
    }

    private fun createTestExecutionListeners(): List<TestExecutionListener> = buildList {
        if (OutputFormat.Pretty in formats) {
            val terminal = Terminal(
                ansiLevel = ansiLevel,
                hyperlinks = when (hyperlinks) {
                    HyperlinkSupport.Auto -> null
                    HyperlinkSupport.On -> true
                    HyperlinkSupport.Off -> false
                },
            )
            add(ConsolePrintingTestExecutionListener(terminal))
        }
        if (OutputFormat.TeamCity in formats) {
            // We capture System.out before JUnit intercepts it to capture test stdout/stderr
            // Otherwise, JUnit captures the service messages as part of the test output.
            val uncapturedOutputStream = System.out
            add(TeamCityMessagesTestExecutionListener(serviceMessagesStream = uncapturedOutputStream))
        }
        if (reportsDir != null) {
            add(LegacyXmlReportGeneratingListener(reportsDir, PrintWriter(System.err)))
        }
    }

    private fun runTests(testExecutionListeners: List<TestExecutionListener>): TestExecutionSummary {
        val request = discoveryRequest()
        val summaryListener = SummaryGeneratingListener()

        LauncherFactory.openSession().use { session ->
            withClasspath(testRuntimeClasspath) {
                val testPlan = session.launcher.discover(request)
                if (!testPlan.containsTests()) {
                    System.err.println("No tests found for the given filters")
                    exitProcess(2)
                }
                session.launcher.execute(testPlan, summaryListener, *testExecutionListeners.toTypedArray())
            }
        }
        return summaryListener.summary ?: error("Couldn't get test summary, has the test plan been run?")
    }

    private fun discoveryRequest(): LauncherDiscoveryRequest =
        LauncherDiscoveryRequestBuilder.request().apply {
            selectMethods.forEach {
                selectors(DiscoverySelectors.selectMethod(it))
            }
            selectClass.forEach {
                selectors(DiscoverySelectors.selectClass(it))
            }
            includeClasses.forEach {
                filters(ClassNameFilter.includeClassNamePatterns(it))
            }
            excludeClasses.forEach {
                filters(ClassNameFilter.excludeClassNamePatterns(it))
            }
            if (includeClasses.isNotEmpty() || excludeClasses.isNotEmpty() || (selectMethods.isEmpty() && selectClass.isEmpty())) {
                selectors(DiscoverySelectors.selectClasspathRoots(testDiscoveryClasspath.toSet()))
            }
        }.build()

    private fun withClasspath(classpath: List<Path>, block: () -> Unit) {
        val originalContext = Thread.currentThread().contextClassLoader
        val testClasspathUrls = classpath.map { it.toUri().toURL() }
        URLClassLoader("test-runtime-classpath", testClasspathUrls.toTypedArray(), originalContext).use { classLoaderWithTests ->
            Thread.currentThread().contextClassLoader = classLoaderWithTests
            try {
                block()
            } finally {
                Thread.currentThread().contextClassLoader = originalContext
            }
        }
    }

    private fun printSummary(summary: TestExecutionSummary) {
        PrintWriter(System.out).use { outWriter ->
            summary.printFailuresTo(outWriter)
            outWriter.println()
            summary.printTo(outWriter)
        }
    }
}

private enum class OutputFormat {
    TeamCity,
    Pretty,
}

private enum class HyperlinkSupport {
    Auto,
    On,
    Off,
}
