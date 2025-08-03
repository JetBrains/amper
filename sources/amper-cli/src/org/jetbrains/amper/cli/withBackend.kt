/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.jetbrains.amper.cli.commands.RootCommand
import org.jetbrains.amper.cli.logging.LoggingInitializer
import org.jetbrains.amper.cli.telemetry.TelemetryEnvironment
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.diagnostics.AsyncProfilerMode
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.plugins.preparePlugins
import org.jetbrains.amper.tasks.AllRunSettings
import org.jetbrains.amper.telemetry.use
import java.util.concurrent.atomic.AtomicReference

private val backendInitialized = AtomicReference<Throwable>(null)

internal suspend fun <T> withBackend(
    commonOptions: RootCommand.CommonOptions,
    commandName: String,
    terminal: Terminal,
    runSettings: AllRunSettings = AllRunSettings(),
    taskExecutionMode: TaskExecutor.Mode = TaskExecutor.Mode.FAIL_FAST,
    setupEnvironment: Boolean = true,
    block: suspend (AmperBackend) -> T,
): T {
    val initializedException = backendInitialized.getAndSet(Throwable())
    if (initializedException != null) {
        throw IllegalStateException("withBackend was already called, see nested exception", initializedException)
    }

    // TODO think of a better place to activate it. e.g. we need it in tests too
    // TODO disabled jul bridge for now since it reports too much in debug mode
    //  and does not handle source class names from jul LogRecord
    // JulTinylogBridge.activate()

    return withContext(Dispatchers.Default) {

        val cliContext = CliContext.create(
            commandName = commandName,
            explicitProjectRoot = commonOptions.explicitProjectRoot,
            explicitBuildOutputRoot = commonOptions.explicitBuildOutputRoot,
            userCacheRoot = commonOptions.sharedCachesRoot,
            terminal = terminal,
            runSettings = runSettings,
        )

        TelemetryEnvironment.setLogsRootDirectory(cliContext.currentLogsRoot)

        if (setupEnvironment) {
            spanBuilder("Setup file logging and monitoring").use {
                DeadLockMonitor.install(cliContext.currentLogsRoot)
                LoggingInitializer.setupFileLogging(cliContext.currentLogsRoot)

                if (commonOptions.asyncProfiler) {
                    AsyncProfilerMode.attachAsyncProfiler(cliContext.currentLogsRoot, cliContext.buildOutputRoot)
                }
            }
        }

        preparePlugins(context = cliContext)

        val backgroundScope = childScope("project background scope")
        val backend = AmperBackend(
            context = cliContext,
            taskExecutionMode = taskExecutionMode,
            backgroundScope = backgroundScope,
        )
        spanBuilder("Run command with backend").use {
            block(backend)
        }.also {
            spanBuilder("Await background scope completion").use {
                cancelAndWaitForScope(backgroundScope)
            }
        }
    }
}

private fun CoroutineScope.childScope(name: String): CoroutineScope =
    CoroutineScope(coroutineContext + SupervisorJob(parent = coroutineContext.job) + CoroutineName(name))

private suspend fun cancelAndWaitForScope(scope: CoroutineScope) {
    val normalTerminationMessage = "terminating scope normally"

    try {
        val job = scope.coroutineContext.job
        job.cancel(normalTerminationMessage)
        job.join()
    } catch (t: Throwable) {
        if (t.message != normalTerminationMessage) {
            throw t
        }
    }
}
