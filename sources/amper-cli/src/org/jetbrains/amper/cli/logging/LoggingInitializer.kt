/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.logging

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperVersion
import org.tinylog.Level
import org.tinylog.core.TinylogLoggingProvider
import org.tinylog.provider.ProviderRegistry
import kotlin.io.path.writeText

object LoggingInitializer {

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
}
