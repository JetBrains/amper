/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.engine.requireSingleDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.tasks.BaseTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.TestTask
import org.jetbrains.amper.tasks.native.NativeLinkTask
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString

class IosKotlinTestTask(
    override val taskName: TaskName,
    override val module: PotatoModule,
    private val projectRoot: AmperProjectRoot,
    private val terminal: Terminal,
    override val platform: Platform,
) : TestTask {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val compileTaskResult = dependenciesResult.requireSingleDependency<NativeLinkTask.Result>()
        val workingDir = module.source.moduleDir ?: projectRoot.path
        val executable = compileTaskResult.linkedBinary
        val chosenDevice = queryDevices().firstOrNull() ?: error("No available device")

        return spanBuilder("ios-native-test")
            .setAttribute("executable", executable.pathString)
            .useWithScope { span ->
                bootAndWaitSimulator(chosenDevice.deviceId)

                val spawnTestsCommand = listOfNotNull(
                    "simctl",
                    "spawn",
                    chosenDevice.deviceId,
                    executable.absolutePathString(),
                    "--",
                    "--ktest_logger=TEAMCITY",
                ).toTypedArray()

                val result = BuildPrimitives.runProcessAndGetOutput(
                    workingDir,
                    *spawnTestsCommand,
                    logCall = true,
                    span = span,
                    outputListener = PrintToTerminalProcessOutputListener(terminal),
                )
                if (result.exitCode != 0) {
                    userReadableError("Kotlin/Native $platform tests failed for module '${module.userReadableName}' with exit code ${result.exitCode} (see errors above)")
                }
                BaseTaskResult(dependenciesResult)
            }
    }
}
