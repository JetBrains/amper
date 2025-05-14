/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.engine.TestTask
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import kotlin.io.path.pathString

class NativeTestTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    private val projectRoot: AmperProjectRoot,
    private val commonRunSettings: CommonRunSettings,
    override val platform: Platform,
    override val buildType: BuildType,
    private val terminal: Terminal,
) : TestTask {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        DeadLockMonitor.disable()

        val compileTaskResult = dependenciesResult.filterIsInstance<NativeLinkTask.Result>().singleOrNull()
            ?: error("Could not find a single compile task in dependencies of $taskName")

        val executable = compileTaskResult.linkedBinary
        if (executable == null) {
            logger.info("No test binary was found for ${platform.pretty}, skipping test run")
            return EmptyTaskResult
        }

        val command = buildList {
            add(executable.pathString)
            addAll(commonRunSettings.toNativeTestExecutableArgs())
        }

        return spanBuilder("native-test")
            .setAmperModule(module)
            .setAttribute("executable", command.first())
            .setListAttribute("args", command.drop(1))
            .use { span ->
                logger.info("Testing module '${module.userReadableName}' for platform '${platform.pretty}'...")

                val workingDir = module.source.moduleDir ?: projectRoot.path

                val result = BuildPrimitives.runProcessAndGetOutput(
                    workingDir = workingDir,
                    command = command,
                    span = span,
                    outputListener = PrintToTerminalProcessOutputListener(terminal),
                )
                if (result.exitCode != 0) {
                    userReadableError("Kotlin/Native $platform tests failed for module '${module.userReadableName}' with exit code ${result.exitCode} (see errors above)")
                }

                EmptyTaskResult
            }
    }
}
