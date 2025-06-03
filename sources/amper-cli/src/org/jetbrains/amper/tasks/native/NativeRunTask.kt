/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.engine.RunTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.pathString

class NativeRunTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    override val platform: Platform,
    override val buildType: BuildType,
    private val commonRunSettings: CommonRunSettings,
    private val terminal: Terminal,
) : RunTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.NATIVE))
    }

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        DeadLockMonitor.disable()

        val compileTaskResult = dependenciesResult.filterIsInstance<NativeLinkTask.Result>().singleOrNull()
            ?: error("Could not find a single compile task in dependencies of $taskName")

        val executable = checkNotNull(compileTaskResult.linkedBinary) { "Executable must always be linked" }
        val programArgs = commonRunSettings.programArgs

        return spanBuilder("native-run")
            .setAttribute("executable", executable.pathString)
            .setListAttribute("args", programArgs)
            .use { span ->
                val result = BuildPrimitives.runProcessAndGetOutput(
                    workingDir = commonRunSettings.workingDir ?: Path(System.getProperty("user.dir")),
                    command = listOf(executable.pathString) + programArgs,
                    span = span,
                    outputListener = PrintToTerminalProcessOutputListener(terminal),
                    input = ProcessInput.Inherit,
                )

                val message = "Process exited with exit code ${result.exitCode}"
                if (result.exitCode != 0) {
                    userReadableError(message, result.exitCode)
                } else {
                    logger.info(message)
                }

                EmptyTaskResult
            }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
