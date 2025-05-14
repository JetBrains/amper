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
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import java.nio.file.Path

/**
 * Task that runs an executable JAR file created by [ExecutableJarTask]
 */
class ExecutableJarRunTask(
    taskName: TaskName,
    module: AmperModule,
    userCacheRoot: AmperUserCacheRoot,
    projectRoot: AmperProjectRoot,
    tempRoot: AmperProjectTempRoot,
    terminal: Terminal,
    commonRunSettings: CommonRunSettings,
    executeOnChangedInputs: ExecuteOnChangedInputs? = null,
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
    override val buildType get() = BuildType.Release

    override suspend fun getJvmArgs(dependenciesResult: List<TaskResult>): List<String> =
        // Add -jar and the jar path to JVM args
        commonRunSettings.userJvmArgs + listOf("-ea", "-jar", findExecutableJarPath(dependenciesResult).toString())

    override suspend fun getClasspath(dependenciesResult: List<TaskResult>): List<Path> =
        // When using -jar, the classpath is ignored, so we return an empty list
        emptyList()

    override suspend fun getMainClass(dependenciesResult: List<TaskResult>): String =
        // When using -jar, the main class is specified in the JAR manifest, so we return dummy
        "dummy"

    private fun findExecutableJarPath(dependenciesResult: List<TaskResult>): Path =
        dependenciesResult.filterIsInstance<ExecutableJarTask.Result>().singleOrNull()?.jarPath
            ?: error("Could not find a suitable JAR task result in dependencies of ${taskName.name}")
}
