/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.completion.SuspendingCompletionCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.warning
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Kernel32Util
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.cli.AmperHelpFormatter
import org.jetbrains.amper.cli.AmperVersion
import org.jetbrains.amper.cli.commands.show.ShowCommand
import org.jetbrains.amper.cli.commands.tools.ToolCommand
import org.jetbrains.amper.cli.createMordantTerminal
import org.jetbrains.amper.cli.logging.LoggingInitializer
import org.jetbrains.amper.cli.options.choiceWithTypoSuggestion
import org.jetbrains.amper.cli.telemetry.TelemetryEnvironment
import org.jetbrains.amper.cli.unwrap
import org.jetbrains.amper.cli.withShowCommandSuggestions
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.tinylog.Level
import java.io.PrintStream
import java.nio.file.Path

internal class RootCommand : SuspendingCliktCommand(name = "amper") {

    init {
        versionOption(
            version = AmperBuild.mavenVersion,
            names = setOf("--version", "-v"),
            message = { AmperVersion.banner },
        )
        subcommands(
            BuildCommand(),
            CleanCommand(),
            CleanSharedCachesCommand(),
            SuspendingCompletionCommand(
                help = "Generate a tab-completion script for the Amper command for the given shell (bash, zsh, or fish)",
            ),
            InitCommand(),
            PackageCommand(),
            PublishCommand(),
            RunCommand(),
            ServerCommand(),
            ShowCommand(),
            TaskCommand(),
            TestCommand(),
            ToolCommand(),
            UpdateCommand(),
        )
        context {
            // one would be created by default, but we manually set it to customize the theme
            terminal = createMordantTerminal()
            helpFormatter = { context -> AmperHelpFormatter(context) }
            suggestTypoCorrection = suggestTypoCorrection.withShowCommandSuggestions()
        }
    }

    private val root by option(help = "Amper project root")
        .path(mustExist = true, canBeFile = false, canBeDir = true)

    private val consoleLogLevel by option(
        "--log-level",
        help = "Console logging level"
    ).choiceWithTypoSuggestion(
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
        .default(AmperUserCacheRoot.fromCurrentUserResult().unwrap())

    private val buildOutputRoot by option(
        "--build-output",
        help = "Root directory for build outputs. By default, this is the `build` directory under the project root."
    ).path(mustExist = false, canBeFile = false, canBeDir = true)

    private val debuggingOptions by DebuggingOptions()

    override suspend fun run() {
        // Ensure we're writing traces to the configured user cache (we start with the default in early telemetry).
        // For commands that have a project context, the traces will eventually be moved to the project build logs dir.
        TelemetryEnvironment.setUserCacheRoot(sharedCachesRoot)

        currentContext.obj = CommonOptions(
            explicitProjectRoot = root,
            consoleLogLevel = consoleLogLevel,
            profilerEnabled = debuggingOptions.profilerEnabled,
            sharedCachesRoot = sharedCachesRoot,
            explicitBuildOutputRoot = buildOutputRoot,
        )

        spanBuilder("Setup console logging").use {
            LoggingInitializer.setupConsoleLogging(consoleLogLevel = consoleLogLevel, terminal = terminal)
        }

        fixSystemOutEncodingOnWindows()

        if (debuggingOptions.coroutinesDebugEnabled) {
            if (isWindowsArm64()) {
                // Always fails on Windows Arm64 because ByteBuddy doesn't support it:
                // https://github.com/raphw/byte-buddy/issues/1336
                terminal.warning("Coroutines debug probes are not supported on Windows Arm64")
            } else {
                installCoroutinesDebugProbes()
            }
        }
    }

    data class CommonOptions(
        /**
         * The explicit project root provided by the user, or null if the root should be discovered.
         */
        val explicitProjectRoot: Path?,
        val consoleLogLevel: Level,
        val profilerEnabled: Boolean,
        val sharedCachesRoot: AmperUserCacheRoot,
        val explicitBuildOutputRoot: Path?,
    )

    /**
     * Some Windows encoding used by default doesn't support symbols used in `show dependencies` output
     * Updating it to UTF-8 solves the issue.
     *
     * See https://github.com/ajalt/mordant/issues/249 for details.
     */
    private fun fixSystemOutEncodingOnWindows() {
        if (!isWindows()) return
        if (System.out.charset() == Charsets.UTF_8) return

        spanBuilder("Fix stdout encoding").useWithoutCoroutines {
            // Set the console code page to 65001 = UTF-8
            val success = Kernel32.INSTANCE.SetConsoleOutputCP(65001)
            if (success) {
                // Replace System.out and System.err with PrintStreams using UTF-8
                System.setOut(PrintStream(System.out, true, Charsets.UTF_8))
                System.setErr(PrintStream(System.err, true, Charsets.UTF_8))
            } else {
                terminal.warning("Failed to set UTF-8 as console output encoding: ${Kernel32Util.getLastErrorMessage()}")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun installCoroutinesDebugProbes() {
        spanBuilder("Install coroutines debug probes").useWithoutCoroutines {
            // coroutines debug probes, required to dump coroutines
            try {
                DebugProbes.install()
            } catch (e: Throwable) {
                terminal.warning("Failed to install coroutines debug probes: $e")
            }
        }
    }

    private fun isWindowsArm64(): Boolean = isWindows() && System.getProperty("os.arch") == "aarch64"

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}

private class DebuggingOptions : OptionGroup(name = "Debugging options") {
    val profilerEnabled by option(
        "--profile",
        help = "Profile Amper with the [Async Profiler](https://github.com/async-profiler/async-profiler). " +
                "The snapshot file is generated in the build logs."
    ).flag(default = false)

    val coroutinesDebugEnabled by option(
        "--coroutines-debug",
        help = "Enable coroutines debug probes. This allows to dump the running coroutines in case of deadlock.",
    ).flag(default = false)
}
