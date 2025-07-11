/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.composehotreload.recompiler.ENV_AMPER_BUILD_ROOT
import org.jetbrains.amper.composehotreload.recompiler.ENV_AMPER_BUILD_TASK
import org.jetbrains.amper.composehotreload.recompiler.ENV_AMPER_SERVER_PORT
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.jdk.provisioning.JdkDownloader
import org.jetbrains.amper.run.ToolingArtifactsDownloader
import org.jetbrains.amper.tasks.JvmMainRunSettings
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.io.path.pathString

class JvmHotRunTask(
    taskName: TaskName,
    module: AmperModule,
    userCacheRoot: AmperUserCacheRoot,
    projectRoot: AmperProjectRoot,
    tempRoot: AmperProjectTempRoot,
    terminal: Terminal,
    runSettings: JvmMainRunSettings,
    executeOnChangedInputs: ExecuteOnChangedInputs,
    private val toolingArtifactsDownloader: ToolingArtifactsDownloader = ToolingArtifactsDownloader(
        userCacheRoot,
        executeOnChangedInputs
    ),
) : AbstractJvmRunTask(
    taskName,
    module,
    userCacheRoot,
    projectRoot,
    tempRoot,
    terminal,
    runSettings,
    executeOnChangedInputs
) {
    override val buildType: BuildType
        get() = BuildType.Debug

    private val portAvailable: Int get() = ServerSocket(0).use { it.localPort }

    override suspend fun getJvmArgs(dependenciesResult: List<TaskResult>): List<String> {
        val agentClasspath = toolingArtifactsDownloader.downloadHotReloadAgent()
        val agent =
            agentClasspath.singleOrNull { it.pathString.contains("org/jetbrains/compose/hot-reload/hot-reload-agent") }
                ?: error("Can't find hot-reload-agent in agent classpath: $agentClasspath")

        val devToolsClasspath = toolingArtifactsDownloader.downloadDevTools()

        val amperJvmArgs = buildList {
            add("-ea")
//            add("-agentlib:jdwp=transport=dt_socket,server=n,address=localhost:5007,suspend=y")
            add("-XX:+AllowEnhancedClassRedefinition")
            add("-javaagent:${agent.pathString}")
            add("-Dcompose.reload.devToolsClasspath=${devToolsClasspath.joinToString(":")}")
            add("-Dcompose.reload.devToolsEnabled=true")
            add("-Dcompose.reload.devToolsTransparencyEnabled=true")
            add("-Dcompose.reload.dirtyResolveDepthLimit=5")
            add("-Dcompose.reload.virtualMethodResolveEnabled=true")
            add("-Damper.build.task=${HotReloadTaskType.Reload.getTaskName(module, platform, isTest = false).name}")
        }

        return amperJvmArgs + runSettings.userJvmArgs
    }

    override suspend fun getClasspath(dependenciesResult: List<TaskResult>): List<Path> {
        val classpath = super.getClasspath(dependenciesResult)
        val agentClasspath = toolingArtifactsDownloader.downloadHotReloadAgent()
        val agent = agentClasspath.singleOrNull { it.pathString.contains("org/jetbrains/compose/hot-reload/hot-reload-agent") }
            ?: error("Can't find hot-reload-agent in agent classpath: $agentClasspath")
        val filteredAgentClasspath = agentClasspath.filter { !it.pathString.contains(agent.pathString) }

        return buildList {
            addAll(classpath)
            addAll(filteredAgentClasspath)

            val hasSl4fjApi = classpath.any { it.pathString.contains("org/slf4j/slf4j-api") }
            if (!hasSl4fjApi) {
                addAll(toolingArtifactsDownloader.downloadSlf4jApi())
            }

            val hasComposeDesktop = classpath.any { it.pathString.contains("org/jetbrains/compose/desktop") }
            if (!hasComposeDesktop) {
                addAll(toolingArtifactsDownloader.downloadComposeDesktop())
            }
        }
    }

    override suspend fun getJdk(): Jdk {
        return JdkDownloader.getJbr(userCacheRoot)
    }

    override fun getEnvironment(dependenciesResult: List<TaskResult>): Map<String, String> = mapOf(
        ENV_AMPER_SERVER_PORT to portAvailable.toString(),
        ENV_AMPER_BUILD_TASK to HotReloadTaskType.Reload.getTaskName(module, platform, isTest = false).name,
        ENV_AMPER_BUILD_ROOT to projectRoot.path.pathString,
    )
}
