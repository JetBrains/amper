/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.BuildPrimitives
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.PotatoModuleProgrammaticSource
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.RunTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import kotlin.io.path.pathString

class NativeRunTask(
    override val taskName: TaskName,
    override val module: PotatoModule,
    override val platform: Platform,
    private val projectRoot: AmperProjectRoot,
    private val commonRunSettings: CommonRunSettings,
    private val terminal: Terminal,
) : RunTask {
    init {
        require(platform.isLeaf)
        require(platform.isDescendantOf(Platform.NATIVE))
    }

    override val buildType: BuildType
        get() = BuildType.Debug

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        DeadLockMonitor.disable()

        val compileTaskResult = dependenciesResult.filterIsInstance<NativeCompileTask.TaskResult>().singleOrNull()
            ?: error("Could not find a single compile task in dependencies of $taskName")

        val executable = compileTaskResult.artifact
        val programArgs = commonRunSettings.programArgs

        return spanBuilder("native-run")
            .setAttribute("executable", executable.pathString)
            .setListAttribute("args", programArgs)
            .useWithScope { span ->
                val workingDir = when (val source = module.source) {
                    is PotatoModuleFileSource -> source.moduleDir
                    PotatoModuleProgrammaticSource -> projectRoot.path
                }

                val result = BuildPrimitives.runProcessAndGetOutput(
                    command = listOf(executable.pathString) + programArgs,
                    workingDir = workingDir,
                    span = span,
                    printOutputToTerminal = terminal,
                )

                val message = "Process exited with exit code ${result.exitCode}" +
                        (if (result.stderr.isNotEmpty()) "\nSTDERR:\n${result.stderr}\n" else "") +
                        (if (result.stdout.isNotEmpty()) "\nSTDOUT:\n${result.stdout}\n" else "")
                if (result.exitCode != 0) {
                    logger.error(message)
                } else {
                    logger.info(message)
                }

                // TODO Should non-zero exit code fail the task somehow?

                object : TaskResult {
                    override val dependencies: List<TaskResult> = dependenciesResult
                }
            }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
