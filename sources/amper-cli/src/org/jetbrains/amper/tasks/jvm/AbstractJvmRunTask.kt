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
import org.jetbrains.amper.engine.RunTask
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.jdk.provisioning.JdkDownloader
import org.jetbrains.amper.jvm.getEffectiveJvmMainClass
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.JvmMainRunSettings
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.workingDir
import org.slf4j.LoggerFactory
import java.nio.file.Path

abstract class AbstractJvmRunTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    protected val userCacheRoot: AmperUserCacheRoot,
    protected val projectRoot: AmperProjectRoot,
    protected val tempRoot: AmperProjectTempRoot,
    protected val terminal: Terminal,
    protected val runSettings: JvmMainRunSettings,
    protected val executeOnChangedInputs: ExecuteOnChangedInputs?,
) : RunTask {
    override val platform = Platform.JVM
    protected val fragments = module.fragments.filter { !it.isTest && it.platforms.contains(Platform.JVM) }

    protected val logger = LoggerFactory.getLogger(javaClass)

    protected open fun getEnvironment(dependenciesResult: List<TaskResult>): Map<String, String> = emptyMap()

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        DeadLockMonitor.disable()

        val result = getJdk().runJava(
            workingDir = runSettings.workingDir,
            mainClass = getMainClass(dependenciesResult),
            classpath = getClasspath(dependenciesResult),
            programArgs = runSettings.programArgs,
            jvmArgs = getJvmArgs(dependenciesResult),
            outputListener = PrintToTerminalProcessOutputListener(terminal),
            tempRoot = tempRoot,
            input = ProcessInput.Inherit,
            environment = getEnvironment(dependenciesResult),
        )

        val message = "Process exited with exit code ${result.exitCode}"
        if (result.exitCode != 0) {
            userReadableError(message, result.exitCode)
        } else {
            logger.info(message)
        }

        return EmptyTaskResult
    }

    protected open suspend fun getJdk(): Jdk = JdkDownloader.getJdk(userCacheRoot)

    protected open suspend fun getJvmArgs(dependenciesResult: List<TaskResult>): List<String> = buildList {
        if (fragments.any { it.settings.ktor.enabled }) {
            add("-Dio.ktor.development=true")
        }
        add("-ea")
        addAll(runSettings.userJvmArgs)
    }

    protected open suspend fun getClasspath(dependenciesResult: List<TaskResult>): List<Path> {
        val runtimeClasspathTask = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>().singleOrNull()
            ?: error("Could not find a single ${JvmRuntimeClasspathTask.Result::class.simpleName} in dependencies of ${taskName.name}")
        return runtimeClasspathTask.jvmRuntimeClasspath
    }

    protected open suspend fun getMainClass(dependenciesResult: List<TaskResult>): String =
        runSettings.userJvmMainClass ?: fragments.getEffectiveJvmMainClass()
}
