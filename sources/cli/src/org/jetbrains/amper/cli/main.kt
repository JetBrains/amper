/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parsers.CommandLineParser
import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.commands.RootCommand
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import java.time.Instant
import kotlin.system.exitProcess

suspend fun main(args: Array<String>) {
    try {
        val mainStartTime = Instant.now()
        TelemetryEnvironment.setup()
        spanBuilder("Root")
            .setStartTimestamp(mainStartTime)
            .setListAttribute("args", args.toList())
            .use {
                // we add a fake span here to represent the telemetry setup
                spanBuilder("Setup telemetry").setStartTimestamp(mainStartTime).startSpan().end()

                val rootCommand = spanBuilder("Initialize CLI command definitions").use {
                    RootCommand()
                }
                rootCommand.mainWithTelemetry(args)
            }
    } catch (e: UserReadableError) {
        printUserError(e.message)
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
    val terminal = Terminal()
    val errorStyle = terminal.theme.danger
    terminal.println(errorStyle("\nERROR: $message"), stderr = true)
}
