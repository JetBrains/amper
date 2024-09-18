/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.JdkDownloader
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.diagnostics.DeadLockMonitor
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.jvm.getEffectiveJvmMainClass
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.RunTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import org.slf4j.LoggerFactory

class JvmRunTask(
    override val taskName: TaskName,
    override val module: PotatoModule,
    private val userCacheRoot: AmperUserCacheRoot,
    private val projectRoot: AmperProjectRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val terminal: Terminal,
    private val commonRunSettings: CommonRunSettings,
) : RunTask {
    override val platform = Platform.JVM
    override val buildType: BuildType
        get() = BuildType.Debug

    private val fragments = module.fragments.filter { !it.isTest && it.platforms.contains(Platform.JVM) }

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        DeadLockMonitor.disable()

        val runtimeClasspathTask = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>().singleOrNull()
            ?: error("Could not find a single ${JvmRuntimeClasspathTask.Result::class.simpleName} in dependencies of ${taskName.name}")

        val jdk = JdkDownloader.getJdk(userCacheRoot)

        // TODO how to support options like debugging, xmx etc?
        // TODO some of them should be coming from module files, some from command line
        // ideally ./amper :cli:jvmRun --debug

        // TODO how to customize properties? -ea? -Xmx?
        val jvmArgs = listOf("-ea")

        val workingDir = module.source.moduleDir ?: projectRoot.path

        val result = jdk.runJava(
            workingDir = workingDir,
            mainClass = fragments.getEffectiveJvmMainClass(),
            classpath = runtimeClasspathTask.jvmRuntimeClasspath,
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

    private val logger = LoggerFactory.getLogger(javaClass)
}
