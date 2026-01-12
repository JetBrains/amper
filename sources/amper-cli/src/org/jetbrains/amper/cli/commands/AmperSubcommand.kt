/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.terminal.success
import org.jetbrains.amper.android.AndroidSdkDetector
import org.jetbrains.amper.cli.AndroidHomeRoot
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.project.findProjectContext
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.createDirectories

internal abstract class AmperSubcommand(name: String) : SuspendingCliktCommand(name = name) {
    /**
     * The logger for this command.
     */
    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * The common options that can be passed to the root command.
     */
    protected val commonOptions by requireObject<RootCommand.CommonOptions>()

    /**
     * Creates a [CliContext] representing the current Amper project and CLI environment.
     */
    protected suspend fun createCliProjectContext() = spanBuilder("Create CLI context").use {
        require(commandName.isNotBlank()) { "commandName should not be blank" }

        val projectContext = findProjectContext(
            explicitProjectRoot = commonOptions.explicitProjectRoot,
            explicitBuildRoot = commonOptions.explicitBuildOutputRoot,
        ) ?: userReadableError(
            "No Amper project found in the current directory or above. " +
                    "Make sure you have a project file or a module file at the root of your Amper project, " +
                    "or specify --root explicitly to run tasks for a project located elsewhere."
        )

        CliContext(
            commandName = commandName,
            projectContext = projectContext,
            userCacheRoot = commonOptions.sharedCachesRoot,
            terminal = terminal,
        )
    }

    /**
     * Prints a message to the console with the 'success' style, and conclusion formatting.
     */
    fun printSuccessfulCommandConclusion(message: String) {
        terminal.success(message)
    }
}
