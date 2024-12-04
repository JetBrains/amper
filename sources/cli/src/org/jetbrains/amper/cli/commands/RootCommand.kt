/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.completion.SuspendingCompletionCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.CliEnvironmentInitializer
import org.jetbrains.amper.cli.commands.tools.ToolCommand
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.use
import org.tinylog.Level
import java.nio.file.Path

internal class RootCommand : SuspendingCliktCommand(name = "amper") {

    init {
        versionOption(
            version = AmperBuild.mavenVersion,
            names = setOf("--version", "-v"),
            message = { AmperBuild.banner },
        )
        subcommands(
            BuildCommand(),
            CleanCommand(),
            CleanSharedCachesCommand(),
            SuspendingCompletionCommand(help = "Generate a tab-completion script for the Amper command for the given shell"),
            InitCommand(),
            ModulesCommand(),
            PublishCommand(),
            RunCommand(),
            SettingsCommand(),
            TaskCommand(),
            TasksCommand(),
            TestCommand(),
            ToolCommand(),
            UpdateCommand(),
        )
        context {
            helpFormatter = { context ->
                object : MordantHelpFormatter(context, showDefaultValues = true) {
                    override fun renderRepeatedMetavar(metavar: String): String {
                        // make it clear that arguments should be separated by '--'
                        if (metavar in setOf("[<app_arguments>]", "[<tool_arguments>]", "[<jaeger_arguments>]")) {
                            return "-- ${metavar}..."
                        }
                        return super.renderRepeatedMetavar(metavar)
                    }
                }
            }
        }
    }

    private val root by option(help = "Amper project root")
        .path(mustExist = true, canBeFile = false, canBeDir = true)

    private val consoleLogLevel by option(
        "--log-level",
        help = "Console logging level"
    ).choice(
        mapOf(
            "debug" to Level.DEBUG,
            "info" to Level.INFO,
            "warn" to Level.WARN,
            "error" to Level.ERROR,
            "off" to Level.OFF,
        ), ignoreCase = true
    ).default(Level.INFO)

    private val sharedCachesRoot by option(
        "--shared-caches-root",
        help = "Path to the cache directory shared between all Amper projects",
    )
        .path(canBeFile = false)
        .convert { AmperUserCacheRoot(it.toAbsolutePath()) }
        // It's ok to use a non-lazy default here because most of the time we'll use the default value anyway.
        // Detecting this path eagerly allows showing the default value in the help.
        .default(AmperUserCacheRoot.fromCurrentUser())

    private val asyncProfiler by option(help = "Profile Amper with Async Profiler").flag(default = false)

    private val buildOutputRoot by option(
        "--build-output",
        help = "Root directory for build outputs. By default, this is the 'build' directory under the project root."
    ).path(mustExist = false, canBeFile = false, canBeDir = true)

    override suspend fun run() {
        val terminal = spanBuilder("Initialize Mordant Terminal").use {
            Terminal()
        }

        currentContext.obj = CommonOptions(
            explicitRoot = root,
            consoleLogLevel = consoleLogLevel,
            asyncProfiler = asyncProfiler,
            sharedCachesRoot = sharedCachesRoot,
            buildOutputRoot = buildOutputRoot,
            terminal = terminal,
        )

        spanBuilder("Setup console logging").use {
            CliEnvironmentInitializer.setupConsoleLogging(
                consoleLogLevel = consoleLogLevel,
                terminal = terminal,
            )
        }
    }

    data class CommonOptions(
        /**
         * The explicit project root provided by the user, or null if the root should be discovered.
         */
        val explicitRoot: Path?,
        val consoleLogLevel: Level,
        val asyncProfiler: Boolean,
        val sharedCachesRoot: AmperUserCacheRoot,
        val buildOutputRoot: Path?,
        val terminal: Terminal,
    )
}
