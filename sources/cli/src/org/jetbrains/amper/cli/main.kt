/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.clikt.command.main
import org.jetbrains.amper.cli.commands.RootCommand
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.use
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.engine.TaskExecutor
import kotlin.system.exitProcess

suspend fun main(args: Array<String>) {
    try {
        TelemetryEnvironment.setup()
        spanBuilder("Root")
            .setListAttribute("args", args.toList())
            .use {
                RootCommand().main(args)
            }
    } catch (t: Throwable) {
        System.err.println()
        System.err.println("ERROR: ${t.message}")

        when {
            t is UserReadableError -> System.err.println()
            t is TaskExecutor.TaskExecutionFailed && t.cause is UserReadableError -> System.err.println()
            else -> t.printStackTrace()
        }

        exitProcess(1)
    }
}
