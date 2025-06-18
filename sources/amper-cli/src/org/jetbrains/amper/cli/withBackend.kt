/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.warning
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Kernel32Util
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.amper.cli.commands.RootCommand
import org.jetbrains.amper.cli.logging.LoggingInitializer
import org.jetbrains.amper.cli.telemetry.TelemetryEnvironment
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.diagnostics.AsyncProfilerMode
import org.jetbrains.amper.diagnostics.CoroutinesDebug
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.tasks.AllRunSettings
import org.jetbrains.amper.telemetry.use
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories

private val backendInitialized = AtomicReference<Throwable>(null)

internal suspend fun <T> withBackend(
    commonOptions: RootCommand.CommonOptions,
    currentCommand: String,
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

    fixSystemOutEncodingOnWindows(terminal)

    // TODO think of a better place to activate it. e.g. we need it in tests too
    // TODO disabled jul bridge for now since it reports too much in debug mode
    //  and does not handle source class names from jul LogRecord
    // JulTinylogBridge.activate()

    return withContext(Dispatchers.Default) {
        // it's ok to parallelize this as long as we wait for it before doing coroutines-heavy work
        val coroutinesDebugInstallJob = launch {
            spanBuilder("Setup coroutines instrumentation").use {
                CoroutinesDebug.setupCoroutinesInstrumentation()
            }
        }

        val backgroundScope = childScope("project background scope")

        val cliContext = spanBuilder("Create CLI context").use {
            CliContext.create(
                explicitProjectRoot = commonOptions.explicitRoot?.toAbsolutePath(),
                buildOutputRoot = commonOptions.buildOutputRoot?.let {
                    it.createDirectories()
                    AmperBuildOutputRoot(it.toAbsolutePath())
                },
                userCacheRoot = commonOptions.sharedCachesRoot,
                currentTopLevelCommand = currentCommand,
                runSettings = runSettings,
                taskExecutionMode = taskExecutionMode,
                backgroundScope = backgroundScope,
                terminal = terminal,
            )
        }

        TelemetryEnvironment.setLogsRootDirectory(cliContext.buildLogsRoot)

        // we make sure coroutines debug probes are installed before trying to setup DeadLockMonitor
        // and before executing coroutines-heavy backend work
        coroutinesDebugInstallJob.join()

        if (setupEnvironment) {
            spanBuilder("Setup file logging and monitoring").use {
                DeadLockMonitor.install(cliContext.buildLogsRoot)
                LoggingInitializer.setupFileLogging(cliContext.buildLogsRoot)

                // TODO output version, os and some env to log file only
                val printableLogsPath = cliContext.buildLogsRoot.path.toString().replaceWhitespaces()
                cliContext.terminal.println("Logs are in file://$printableLogsPath")
                cliContext.terminal.println()

                if (commonOptions.asyncProfiler) {
                    AsyncProfilerMode.attachAsyncProfiler(cliContext.buildLogsRoot, cliContext.buildOutputRoot)
                }
            }
        }

        val backend = AmperBackend(context = cliContext)
        spanBuilder("Run command with backend").use {
            block(backend)
        }.also {
            spanBuilder("Await background scope completion").use {
                cancelAndWaitForScope(backgroundScope)
            }
        }
    }
}

private fun fixSystemOutEncodingOnWindows(terminal: Terminal): Boolean {
    if (!System.getProperty("os.name").lowercase().contains("win")) return true

    try {
        // Set console code page to 65001 = UTF-8
        if (Kernel32.INSTANCE.SetConsoleOutputCP(65001)) {
            // Replace System.out and System.err with PrintStreams using UTF-8
            System.setOut(PrintStream(System.out, true, Charsets.UTF_8))
            System.setErr(PrintStream(System.err, true, Charsets.UTF_8))
        } else {
            // SetConsoleOutputCP() failed, throw exception with error message.
            error(Kernel32Util.getLastErrorMessage())
        }
    } catch (t: Throwable) {
        terminal.warning("Failed to set UTF-8 as console output encoding: ${t.message}.")
        return false
    }

    return true
}

private fun CoroutineScope.childScope(name: String): CoroutineScope =
    CoroutineScope(coroutineContext + SupervisorJob(parent = coroutineContext.job) + CoroutineName(name))

private fun String.replaceWhitespaces() = replace(" ", "%20")

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
