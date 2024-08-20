/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.android.AndroidModuleData
import org.jetbrains.amper.android.ResolvedDependency
import org.jetbrains.amper.android.runAndroidBuild
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.repr
import org.jetbrains.amper.util.toAndroidRequestBuildType
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div

abstract class AndroidDelegatedGradleTask(
    private val module: PotatoModule,
    private val buildType: BuildType,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val androidSdkPath: Path,
    private val fragments: List<Fragment>,
    private val projectRoot: AmperProjectRoot,
    private val taskOutputPath: TaskOutputRoot,
    private val buildLogsRoot: AmperBuildLogsRoot,
    override val taskName: TaskName,
) : Task {

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val runtimeClasspath = runtimeClasspath(dependenciesResult)

        val moduleGradlePath = module.gradlePath(projectRoot)
        val androidModuleData = AndroidModuleData(
            modulePath = moduleGradlePath,
            moduleClasses = listOf(),
            resolvedAndroidRuntimeDependencies = runtimeClasspath.map {
                ResolvedDependency("group", "artifact", "version", it)
            },
        )
        val request = AndroidBuildRequest(
            root = projectRoot.path,
            phase = phase,
            modules = setOf(androidModuleData),
            buildTypes = setOf(buildType.toAndroidRequestBuildType),
            sdkDir = androidSdkPath,
            targets = setOf(moduleGradlePath),
        )

        val androidConfig = fragments.joinToString { it.settings.android.repr }
        val configuration = mapOf("androidConfig" to androidConfig)

        val executionResult =
            executeOnChangedInputs.execute(taskName.name, configuration, runtimeClasspath + additionalInputFiles) {
                logger.info("Using android sdk at $androidSdkPath")
                val logFileName = UUID.randomUUID()
                val gradleLogStdoutPath =
                    buildLogsRoot.path / "gradle" / "${this::class.simpleName}-$logFileName.stdout"
                val gradleLogStderrPath =
                    buildLogsRoot.path / "gradle" / "${this::class.simpleName}-$logFileName.stderr"
                gradleLogStdoutPath.createParentDirectories()
                val result = runAndroidBuild(
                    request,
                    taskOutputPath.path / "gradle-project",
                    gradleLogStdoutPath,
                    gradleLogStderrPath,
                    eventHandler = { it.handle(gradleLogStdoutPath, gradleLogStderrPath) })
                ExecuteOnChangedInputs.ExecutionResult(result.filter(::outputFilterPredicate))
            }

        taskOutputPath.path.createDirectories()
        executionResult.outputs.map {
                it.copyToRecursively(
                    taskOutputPath.path.resolve(it.fileName), followLinks = false, overwrite = true
                )
            }
        return result(executionResult.outputs)
    }

    protected abstract val phase: AndroidBuildRequest.Phase

    protected open val additionalInputFiles: List<Path> = listOf()
    protected open fun outputFilterPredicate(path: Path): Boolean = true
    protected open fun result(artifacts: List<Path>): TaskResult = Result(artifacts)
    protected open fun runtimeClasspath(dependenciesResult: List<TaskResult>): List<Path> {
        val runtimeClasspathTaskResult =
            dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>().singleOrNull()
                ?: error("${JvmRuntimeClasspathTask::class.simpleName} result is not found in dependencies of $taskName")
        val runtimeClasspath = runtimeClasspathTaskResult.jvmRuntimeClasspath
        return runtimeClasspath
    }

    class Result(val artifacts: List<Path>) : TaskResult

    private val logger = LoggerFactory.getLogger(this::class.java)
}
