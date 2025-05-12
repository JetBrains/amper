/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.telemetry.setProcessResultAttributes
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.concurrency.StripedMutex
import org.jetbrains.amper.concurrency.withLock
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TestTask
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.native.NativeLinkTask
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString

class IosKotlinTestTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    private val projectRoot: AmperProjectRoot,
    private val terminal: Terminal,
    override val platform: Platform,
    override val buildType: BuildType,
) : TestTask {
    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val compileTaskResult = dependenciesResult.requireSingleDependency<NativeLinkTask.Result>()
        val workingDir = module.source.moduleDir ?: projectRoot.path
        val executable = compileTaskResult.linkedBinary
        if (executable == null) {
            logger.info("No test binary was found for ${platform.pretty}, skipping test run")
            return EmptyTaskResult
        }
        val chosenDevice = pickBestDevice() ?: error("No available device")

        DeviceLock.withLock(hash = chosenDevice.deviceId.hashCode()) {
            val spawnTestsCommand = listOf(
                XCRUN_EXECUTABLE,
                "simctl",
                "spawn",
                chosenDevice.deviceId,
                executable.absolutePathString(),
                "--",
                "--ktest_logger=TEAMCITY",
            )

            return spanBuilder("ios-kotlin-test")
                .setAttribute("executable", spawnTestsCommand.first())
                .setListAttribute("args", spawnTestsCommand.drop(1))
                .use { span ->
                    bootAndWaitSimulator(chosenDevice)

                    val result = BuildPrimitives.runProcessAndGetOutput(
                        workingDir = workingDir,
                        command = spawnTestsCommand,
                        span = span,
                        outputListener = PrintToTerminalProcessOutputListener(terminal),
                    )
                    span.setProcessResultAttributes(result)
                    if (result.exitCode != 0) {
                        userReadableError(
                            "Kotlin/Native $platform tests failed for module " +
                                    "'${module.userReadableName}' with exit code ${result.exitCode} (see errors above)"
                        )
                    }
                    shutdownDevice(chosenDevice.deviceId)
                    EmptyTaskResult
                }
        }
    }

    private companion object {
        // Need to lock the device to avoid racy situations
        // when a parallel task shuts the simulator down while we still use it.
        val DeviceLock = StripedMutex()
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
