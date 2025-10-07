/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.logging.LoggingInitializer
import org.jetbrains.amper.cli.telemetry.TelemetryEnvironment
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.diagnostics.AsyncProfilerMode
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.telemetry.use

/**
 * An [AmperSubcommand] that can only be run in an Amper project.
 */
internal abstract class AmperProjectAwareCommand(name: String) : AmperSubcommand(name) {

    final override suspend fun run() {
        val cliContext = createCliProjectContext()

        spanBuilder("Switch telemetry to project-local build directory").use {
            TelemetryEnvironment.setLogsRootDirectory(cliContext.currentLogsRoot)
        }

        spanBuilder("Setup file logging and monitoring").use {
            DeadLockMonitor.install(cliContext.currentLogsRoot)
            LoggingInitializer.setupFileLogging(cliContext.currentLogsRoot)
        }

        if (commonOptions.asyncProfiler) {
            spanBuilder("Setup profiler").use {
                AsyncProfilerMode.attachAsyncProfiler(cliContext.currentLogsRoot, cliContext.userCacheRoot)
            }
        }

        run(cliContext)
    }

    abstract suspend fun run(cliContext: CliContext)
}