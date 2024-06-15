/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.android.AndroidModuleData
import org.jetbrains.amper.android.ApkPathAndroidBuildResult
import org.jetbrains.amper.android.ResolvedDependency
import org.jetbrains.amper.android.runAndroidBuild
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.tasks.CommonTaskUtils
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmCompileTask
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.repr
import org.jetbrains.amper.util.toAndroidRequestBuildType
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div

class AndroidBuildTask(
    val module: PotatoModule,
    private val buildType: BuildType,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val androidSdkPath: Path,
    private val fragments: List<Fragment>,
    private val taskOutputPath: TaskOutputRoot,
    private val buildLogsRoot: AmperBuildLogsRoot,
    override val taskName: TaskName,
) : Task {
    @OptIn(ExperimentalPathApi::class)
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val rootPath =
            (module.source as? PotatoModuleFileSource)?.buildFile?.parent ?: error("No build file ${module.source}")

        val compileTaskResult = dependenciesResult.filterIsInstance<JvmCompileTask.Result>().singleOrNull()
            ?: error("Could not find a single compile task in dependencies of $taskName")

        val classes = CommonTaskUtils.getRuntimeClasses(compileTaskResult)
        val runtimeClasspath = CommonTaskUtils.buildRuntimeClasspath(compileTaskResult)
        val androidModuleData = AndroidModuleData(":", classes, runtimeClasspath.map {
            ResolvedDependency("group", "artifact", "version", it)
        })
        val request = AndroidBuildRequest(
            rootPath,
            AndroidBuildRequest.Phase.Build,
            setOf(androidModuleData),
            setOf(buildType.toAndroidRequestBuildType),
            sdkDir = androidSdkPath
        )
        val inputs = classes + runtimeClasspath
        val androidConfig = fragments.joinToString { it.settings.android.repr }
        val configuration = mapOf("androidConfig" to androidConfig)
        val executionResult = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            val logFileName = UUID.randomUUID()
            val gradleLogStdoutPath = buildLogsRoot.path / "gradle" / "build-$logFileName.stdout"
            val gradleLogStderrPath = buildLogsRoot.path / "gradle" / "build-$logFileName.stderr"
            gradleLogStdoutPath.createParentDirectories()
            val result = runAndroidBuild(
                request,
                taskOutputPath.path / "gradle-project",
                gradleLogStdoutPath,
                gradleLogStderrPath,
                ApkPathAndroidBuildResult::class.java,
                eventHandler = { it.handle(gradleLogStdoutPath, gradleLogStderrPath) }
            )
            ExecuteOnChangedInputs.ExecutionResult(result.paths.map { Path.of(it) }, mapOf())
        }
        taskOutputPath.path.createDirectories()
        val outputs = executionResult
            .outputs
            .map {
                it.copyToRecursively(
                    taskOutputPath.path.resolve(it.fileName),
                    followLinks = false,
                    overwrite = true
                )
            }
        return Task(dependenciesResult, executionResult.outputs)
    }

    class Task(
        override val dependencies: List<TaskResult>,
        val artifacts: List<Path>,
    ) : TaskResult
}
