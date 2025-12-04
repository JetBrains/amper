/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.parameters.groups.provideDelegate
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.logging.LoggingInitializer
import org.jetbrains.amper.cli.options.ProjectLayoutOptions
import org.jetbrains.amper.cli.telemetry.TelemetryEnvironment
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.diagnostics.Profiler
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use

/**
 * An [AmperSubcommand] that can only be run in an Amper project.
 */
internal abstract class AmperProjectAwareCommand(name: String) : AmperSubcommand(name) {

    protected val layoutOptions by ProjectLayoutOptions()

    final override suspend fun run() {
        val cliContext = createCliProjectContext(
            explicitProjectDir = commonOptions.explicitProjectRoot ?: layoutOptions.explicitProjectDir,
            explicitBuildDir = commonOptions.explicitBuildOutputRoot ?: layoutOptions.explicitBuildDir,
        )

        spanBuilder("Switch telemetry to project-local build directory").use {
            TelemetryEnvironment.setLogsRootDirectory(cliContext.currentLogsRoot)
        }

        spanBuilder("Setup file logging and monitoring").use {
            DeadLockMonitor.install(cliContext.currentLogsRoot)
            LoggingInitializer.setupFileLogging(cliContext.currentLogsRoot)
        }

        if (commonOptions.profilerEnabled) {
            spanBuilder("Setup profiler").use {
                Profiler.start(userCacheRoot = cliContext.userCacheRoot, logsRoot = cliContext.currentLogsRoot)
            }
        }

        run(cliContext)
    }

    abstract suspend fun run(cliContext: CliContext)
}