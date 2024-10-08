/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.intellij.util.namedChildScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.use
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.tasks.CommonRunSettings
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories

private val backendInitialized = AtomicReference<Throwable>(null)

internal suspend fun withBackend(
    commonOptions: RootCommand.CommonOptions,
    currentCommand: String,
    commonRunSettings: CommonRunSettings = CommonRunSettings(),
    taskExecutionMode: TaskExecutor.Mode = TaskExecutor.Mode.FAIL_FAST,
    setupEnvironment: Boolean = true,
    block: suspend (AmperBackend) -> Unit,
) {
    val initializedException = backendInitialized.getAndSet(Throwable())
    if (initializedException != null) {
        throw IllegalStateException("withBackend was already called, see nested exception", initializedException)
    }

    // TODO think of a better place to activate it. e.g. we need it in tests too
    // TODO disabled jul bridge for now since it reports too much in debug mode
    //  and does not handle source class names from jul LogRecord
    // JulTinylogBridge.activate()

    spanBuilder("CLI Setup: install coroutines debug probes").use {
        CliEnvironmentInitializer.setup()
    }

    withContext(Dispatchers.Default) {
        @Suppress("UnstableApiUsage")
        val backgroundScope = namedChildScope("project background scope", supervisor = true)
        commonOptions.terminal.println(AmperBuild.banner)

        val cliContext = spanBuilder("CLI Setup: create CliContext").use {
            CliContext.create(
                explicitProjectRoot = commonOptions.explicitRoot?.toAbsolutePath(),
                buildOutputRoot = commonOptions.buildOutputRoot?.let {
                    it.createDirectories()
                    AmperBuildOutputRoot(it.toAbsolutePath())
                },
                userCacheRoot = commonOptions.sharedCachesRoot,
                currentTopLevelCommand = currentCommand,
                commonRunSettings = commonRunSettings,
                taskExecutionMode = taskExecutionMode,
                backgroundScope = backgroundScope,
                terminal = commonOptions.terminal,
            )
        }

        TelemetryEnvironment.setLogsRootDirectory(cliContext.buildLogsRoot)

        if (setupEnvironment) {
            spanBuilder("CLI Setup: setup logging and monitoring").use {
                CliEnvironmentInitializer.setupDeadLockMonitor(cliContext.buildLogsRoot, cliContext.terminal)
                CliEnvironmentInitializer.setupFileLogging(cliContext.buildLogsRoot)

                // TODO output version, os and some env to log file only
                val printableLogsPath = cliContext.buildLogsRoot.path.toString().replaceWhitespaces()
                cliContext.terminal.println("Logs are in file://$printableLogsPath")
                cliContext.terminal.println()

                if (commonOptions.asyncProfiler) {
                    AsyncProfilerMode.attachAsyncProfiler(cliContext.buildLogsRoot, cliContext.buildOutputRoot)
                }
            }
        }

        spanBuilder("Execute backend").use {
            val backend = AmperBackend(context = cliContext)
            block(backend)
            cancelAndWaitForScope(backgroundScope)
        }
    }
}

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
