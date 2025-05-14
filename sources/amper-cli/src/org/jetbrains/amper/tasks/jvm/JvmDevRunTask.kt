/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jdk.provisioning.Jdk
import org.jetbrains.amper.jdk.provisioning.JdkDownloader
import org.jetbrains.amper.run.ToolingArtifactsDownloader
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path
import kotlin.io.path.pathString

class JvmDevRunTask(
    taskName: TaskName,
    module: AmperModule,
    userCacheRoot: AmperUserCacheRoot,
    projectRoot: AmperProjectRoot,
    tempRoot: AmperProjectTempRoot,
    terminal: Terminal,
    commonRunSettings: CommonRunSettings,
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
    commonRunSettings,
    executeOnChangedInputs
) {
    override val buildType: BuildType
        get() = BuildType.Debug

    override suspend fun getJvmArgs(dependenciesResult: List<TaskResult>): List<String> {
        val agentClasspath = toolingArtifactsDownloader.downloadHotReloadAgent()
        val agent = agentClasspath.singleOrNull { it.pathString.contains("org/jetbrains/compose/hot-reload/agent") }
            ?: error("Can't find hot-reload-agent in agent classpath: $agentClasspath")

        val devToolsClasspath = toolingArtifactsDownloader.downloadDevTools()

        val amperJvmArgs = buildList {
            add("-ea")
//                add("-agentlib:jdwp=transport=dt_socket,server=n,address=localhost:5007,suspend=y")
            add("-XX:+AllowEnhancedClassRedefinition")
            add("-XX:HotswapAgent=external")
            add("-javaagent:${agent.pathString}")
            add("-Dcompose.reload.devToolsClasspath=${devToolsClasspath.joinToString(":")}")
            add("-Dcompose.reload.buildSystem=Amper")
            add("-Damper.build.root=${projectRoot.path}")
            add("-Damper.build.task=${HotReloadTaskType.Reload.getTaskName(module, platform, isTest = false).name}")
        }

        return amperJvmArgs + commonRunSettings.userJvmArgs
    }

    override suspend fun getClasspath(dependenciesResult: List<TaskResult>): List<Path> {
        val classpath = super.getClasspath(dependenciesResult)
        val agentClasspath = toolingArtifactsDownloader.downloadHotReloadAgent()
        val agent = agentClasspath.singleOrNull { it.pathString.contains("org/jetbrains/compose/hot-reload/agent") }
            ?: error("Can't find hot-reload-agent in agent classpath: $agentClasspath")
        val filteredAgentClasspath = agentClasspath.filter { !it.pathString.contains(agent.pathString) }

        val hasSl4fjApi = classpath.any { it.pathString.contains("org/slf4j/slf4j-api") }
        if (!hasSl4fjApi) {
            val sl4fjApi = toolingArtifactsDownloader.downloadSlf4jApi()
            return classpath + sl4fjApi + filteredAgentClasspath
        }
        return classpath + filteredAgentClasspath
    }

    override suspend fun getJdk(): Jdk {
        return JdkDownloader.getJbr(userCacheRoot)
    }
}
