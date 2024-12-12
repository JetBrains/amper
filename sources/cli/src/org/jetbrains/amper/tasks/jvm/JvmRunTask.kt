/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.jvm.getEffectiveJvmMainClass
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.processes.ProcessInput
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.run.ToolingArtifactsDownloader
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.RunTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.compose.isComposeEnabledFor
import org.jetbrains.amper.tasks.compose.isHotReloadEnabledFor
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import kotlin.io.path.pathString

class JvmRunTask(
    override val taskName: TaskName,
    override val module: AmperModule,
    private val userCacheRoot: AmperUserCacheRoot,
    private val projectRoot: AmperProjectRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val terminal: Terminal,
    private val commonRunSettings: CommonRunSettings,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val toolingArtifactsDownloader: ToolingArtifactsDownloader = ToolingArtifactsDownloader(
        userCacheRoot,
        executeOnChangedInputs
    ),
) : RunTask {
    override val platform = Platform.JVM
    override val buildType: BuildType
        get() = BuildType.Debug

    private val fragments = module.fragments.filter { !it.isTest && it.platforms.contains(Platform.JVM) }

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        DeadLockMonitor.disable()

        val runtimeClasspathTask = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>().singleOrNull()
            ?: error("Could not find a single ${JvmRuntimeClasspathTask.Result::class.simpleName} in dependencies of ${taskName.name}")

        val isHotReloadEnabled = isComposeEnabledFor(module) && isHotReloadEnabledFor(module)
        val jdk = if (isHotReloadEnabled) {
            JdkDownloader.getJbr(userCacheRoot)
        } else {
            JdkDownloader.getJdk(userCacheRoot)
        }

        val (amperJvmArgs, agentClasspath) = if (isHotReloadEnabled) {
            val agentClasspath = toolingArtifactsDownloader.downloadHotReloadAgent()
            val agent = agentClasspath.singleOrNull { it.pathString.contains("hot-reload-agent") }
                ?: error("Can't find hot-reload-agent in agent classpath: $agentClasspath")

            val filteredAgentClasspath = agentClasspath.filter { !it.pathString.contains(agent.pathString) }
            
            val devToolsClasspath = toolingArtifactsDownloader.downloadDevTools()

            buildList {
                add("-ea")
//                add("-agentlib:jdwp=transport=dt_socket,server=n,address=localhost:5007,suspend=y")
                add("-XX:+AllowEnhancedClassRedefinition")
                add("-XX:HotswapAgent=external")
                add("-javaagent:${agent.pathString}")
                add("-Dcompose.reload.devToolsClasspath=${devToolsClasspath.joinToString(":")}")
                add("-Dcompose.reload.buildSystem=Amper")
                add("-Damper.build.root=${projectRoot.path}")
                add("-Damper.build.task=${HotReloadTaskType.Reload.getTaskName(module, platform, isTest = false).name}")
            } to filteredAgentClasspath
        } else {
            buildList { add("-ea") } to listOf()
        }

        // TODO also support options from module files? (AMPER-3253)
        val jvmArgs = amperJvmArgs + commonRunSettings.userJvmArgs

        val workingDir = module.source.moduleDir ?: projectRoot.path

        val result = jdk.runJava(
            workingDir = workingDir,
            mainClass = fragments.getEffectiveJvmMainClass(),
            classpath = runtimeClasspathTask.jvmRuntimeClasspath + agentClasspath,
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
