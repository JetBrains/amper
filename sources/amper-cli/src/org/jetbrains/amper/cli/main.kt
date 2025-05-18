/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parsers.CommandLineParser
import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.commands.RootCommand
import org.jetbrains.amper.cli.logging.DoNotLogToTerminalCookie
import org.jetbrains.amper.cli.telemetry.TelemetryEnvironment
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.time.Instant
import kotlin.system.exitProcess

suspend fun main(args: Array<String>) {
    try {
        // We don't use RuntimeMXBean.startTime because it might give incorrect results if the system time changes.
        // The uptime value used to have a bug because of this, but now uses a more precise OS-provided value.
        // See https://bugs.openjdk.org/browse/JDK-6523160
        val jvmUptimeMillisAtMainStart = ManagementFactory.getRuntimeMXBean().uptime
        val mainStartTime = Instant.now()
        val jvmStartTime = mainStartTime.minusMillis(jvmUptimeMillisAtMainStart)

        TelemetryEnvironment.setup()
        spanBuilder("Root")
            .setStartTimestamp(jvmStartTime)
            .setListAttribute("args", args.toList())
            .setListAttribute("jvm-args", ManagementFactory.getRuntimeMXBean().inputArguments)
            .use {
                spanBuilder("JVM startup")
                    .setStartTimestamp(jvmStartTime)
                    .startSpan()
                    .end(mainStartTime)
                // we add a fake span here to represent the telemetry setup
                spanBuilder("Setup telemetry").setStartTimestamp(mainStartTime).startSpan().end()

                val rootCommand = spanBuilder("Initialize CLI command definitions").use {
                    RootCommand()
                }
                rootCommand.mainWithTelemetry(args)
            }
    } catch (e: UserReadableError) {
        printUserError(e.message)
        exitProcess(e.exitCode)
    } catch (e: Exception) {
        printInternalError(e)
        exitProcess(1)
    }
}

/**
 * Parses command line arguments and runs this command.
 */
// This implementation is inlined from CoreSuspendingCliktCommand.main
// to isolate the parsing of CLI arguments for telemetry purposes (FTR, it's ~20ms).
private suspend fun SuspendingCliktCommand.mainWithTelemetry(args: Array<String>) {
    val command = this
    CommandLineParser.main(command) {
        val result = spanBuilder("Parse CLI arguments").use {
            CommandLineParser.parse(command, args.asList())
        }
        CommandLineParser.run(result.invocation) { it.run() }
    }
}

private fun printUserError(message: String) {
    printRedToStderr("\nERROR: $message")
}

private fun printInternalError(e: Exception) {
    // we avoid showing a scary stacktrace in the terminal, but we still provide it in the logs
    printRedToStderr("\nInternal error: $e\n\nPlease check the build logs for the full stacktrace, and if possible file a bug report at https://youtrack.jetbrains.com/newIssue?project=AMPER")
    DoNotLogToTerminalCookie.use {
        LoggerFactory.getLogger("main").error("Internal error:", e)
    }
}

private fun printRedToStderr(message: String) {
    val terminal = Terminal()
    val errorStyle = terminal.theme.danger
    terminal.println(errorStyle(message), stderr = true)
}
