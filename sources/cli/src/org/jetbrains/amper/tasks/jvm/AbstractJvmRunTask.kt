/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jvm.Jdk
import org.jetbrains.amper.jvm.getEffectiveJvmMainClass
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.RunTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path

abstract class AbstractJvmRunTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    protected val userCacheRoot: AmperUserCacheRoot,
    protected val projectRoot: AmperProjectRoot,
    protected val tempRoot: AmperProjectTempRoot,
    protected val terminal: Terminal,
    protected val commonRunSettings: CommonRunSettings,
    protected val executeOnChangedInputs: ExecuteOnChangedInputs,
) : RunTask {
    override val platform = Platform.JVM
    override val buildType: BuildType
        get() = BuildType.Debug

    protected val fragments = module.fragments.filter { !it.isTest && it.platforms.contains(Platform.JVM) }

    protected abstract suspend fun jvmArgs(): List<String>
    protected abstract suspend fun finalClasspath(classpath: List<Path>): List<Path>

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        DeadLockMonitor.disable()

        val runtimeClasspathTask = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>().singleOrNull()
            ?: error("Could not find a single ${JvmRuntimeClasspathTask.Result::class.simpleName} in dependencies of ${taskName.name}")

        val workingDir = module.source.moduleDir ?: projectRoot.path

        val jvmArgs = jvmArgs()
        val classpath = finalClasspath(runtimeClasspathTask.jvmRuntimeClasspath)

        val result = getJdk().runJava(
            workingDir = workingDir,
            mainClass = commonRunSettings.userJvmMainClass ?: fragments.getEffectiveJvmMainClass(),
            classpath = classpath,
            programArgs = commonRunSettings.programArgs,
            jvmArgs = jvmArgs,
            outputListener = PrintToTerminalProcessOutputListener(terminal),
            tempRoot = tempRoot,
            input = ProcessInput.Inherit,
        )

        val message = "Process exited with exit code ${result.exitCode}" +
                (if (result.stderr.isNotEmpty()) "\nSTDERR:\n${result.stderr}\n" else "") +
                (if (result.stdout.isNotEmpty()) "\nSTDOUT:\n${result.stdout}\n" else "")
        if (result.exitCode != 0) {
            userReadableError(message)
        } else {
            logger.info(message)
        }

        return object : TaskResult {}
    }

    protected abstract suspend fun getJdk(): Jdk

    protected val logger = LoggerFactory.getLogger(javaClass)
}
