/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.command.main
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.commands.RootCommand
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.use
import org.jetbrains.amper.diagnostics.setListAttribute
import kotlin.system.exitProcess

suspend fun main(args: Array<String>) {
    try {
        TelemetryEnvironment.setup()
        spanBuilder("Root")
            .setListAttribute("args", args.toList())
            .use {
                RootCommand().main(args)
            }
    } catch (e: UserReadableError) {
        printUserError(e.message)
        exitProcess(1)
    }
}

private fun printUserError(message: String) {
    System.err.println()
    val errorStyle = Theme.Default.danger
    Terminal().println(errorStyle("ERROR: $message"), stderr = true)
    System.err.println()
}
