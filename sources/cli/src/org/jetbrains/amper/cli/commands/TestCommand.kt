/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.cli.withBackend
import org.jetbrains.amper.engine.TaskExecutor

internal class TestCommand : SuspendingCliktCommand(name = "test") {

    val platform by platformOption()

    val filter by option("-f", "--filter", help = "wildcard filter to run only matching tests, the option could be repeated to run tests matching any filter")

    val includeModules by option("-m", "--include-module", help = "specific module to check, the option could be repeated to check several modules").multiple()

    val excludeModules by option("--exclude-module", help = "specific module to exclude, the option could be repeated to exclude several modules").multiple()

    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Run tests in the project"

    override suspend fun run() {
        if (filter != null) {
            userReadableError("Filters are not implemented yet")
        }

        // try to execute as many tests as possible
        withBackend(
            commonOptions,
            commandName,
            taskExecutionMode = TaskExecutor.Mode.GREEDY,
        ) { backend ->
            backend.test(
                platforms = platform.ifEmpty { null }?.toSet(),
                includeModules = if (includeModules.isNotEmpty()) includeModules.toSet() else null,
                excludeModules = excludeModules.toSet(),
            )
        }
    }
}
