/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.terminal.success
import org.jetbrains.amper.cli.CliContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
    protected suspend fun createCliProjectContext() = CliContext.create(
        commandName = commandName,
        explicitProjectRoot = commonOptions.explicitProjectRoot,
        explicitBuildOutputRoot = commonOptions.explicitBuildOutputRoot,
        userCacheRoot = commonOptions.sharedCachesRoot,
        terminal = terminal,
    )

    /**
     * Prints a message to the console with the 'success' style, and conclusion formatting.
     */
    fun printSuccessfulCommandConclusion(message: String) {
        terminal.success(message)
    }
}
