/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.mordant.terminal.Terminal
import dev.reformator.stacktracedecoroutinator.jvm.DecoroutinatorJvmApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import org.jetbrains.amper.cli.logging.DynamicFileWriter
import org.jetbrains.amper.cli.logging.DynamicLevelConsoleWriter
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.tinylog.Level
import org.tinylog.core.TinylogLoggingProvider
import org.tinylog.provider.ProviderRegistry
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.writeText

object CliEnvironmentInitializer {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun setupCoroutinesInstrumentation() {
        // TODO investigate the performance impact of the decoroutinator
        spanBuilder("Install stacktrace-decoroutinator").useWithoutCoroutines {
            // see https://github.com/Anamorphosee/stacktrace-decoroutinator#motivation
            DecoroutinatorJvmApi.install()
        }

        spanBuilder("Install coroutines debug probes").useWithoutCoroutines {
            // coroutines debug probes, required to dump coroutines
            DebugProbes.enableCreationStackTraces = false
            DebugProbes.install()
        }
    }

    fun setupDeadLockMonitor(logsRoot: AmperBuildLogsRoot) {
        DeadLockMonitor.install(logsRoot)
    }

    fun setupConsoleLogging(consoleLogLevel: Level, terminal: Terminal) {
        val loggingProvider = ProviderRegistry.getLoggingProvider() as TinylogLoggingProvider

        val consoleLogger = loggingProvider.writers.filterIsInstance<DynamicLevelConsoleWriter>().single()
        consoleLogger.setLevel(consoleLogLevel)
        consoleLogger.setTerminal(terminal)
    }

    fun setupFileLogging(logsRoot: AmperBuildLogsRoot) {
        val logFileBanner = """
            ${AmperVersion.banner}
            running on ${System.getProperty("os.name")} ${System.getProperty("os.version").lowercase()} jvm arch ${System.getProperty("os.arch")}
        """.trimIndent().trim() + "\n\n"

        val loggingProvider = ProviderRegistry.getLoggingProvider() as TinylogLoggingProvider

        val debugWriter = loggingProvider.writers.filterIsInstance<DynamicFileWriter>().single { it.level == Level.DEBUG }
        val debugLogFile = logsRoot.path.resolve("debug.log")
        debugLogFile.writeText(logFileBanner)
        debugWriter.setFile(debugLogFile)

        val infoWriter = loggingProvider.writers.filterIsInstance<DynamicFileWriter>().single { it.level == Level.INFO }
        val infoLogFile = logsRoot.path.resolve("info.log")
        infoLogFile.writeText(logFileBanner)
        infoWriter.setFile(infoLogFile)
    }

    fun currentTimestamp(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())

}
