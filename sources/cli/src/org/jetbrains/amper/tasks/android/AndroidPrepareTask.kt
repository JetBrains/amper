/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.AndroidBuildRequest
import org.jetbrains.amper.android.AndroidModuleData
import org.jetbrains.amper.android.RClassAndroidBuildResult
import org.jetbrains.amper.android.ResolvedDependency
import org.jetbrains.amper.android.runAndroidBuild
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.AmperProjectRoot
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.AdditionalClasspathProvider
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.repr
import org.jetbrains.amper.util.toAndroidRequestBuildType
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.extension

class AndroidPrepareTask(
    override val taskName: TaskName,
    private val module: PotatoModule,
    private val buildType: BuildType,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val androidSdkPath: Path,
    private val fragments: List<Fragment>,
    private val projectRoot: AmperProjectRoot,
    private val taskOutputRoot: TaskOutputRoot,
    private val buildLogsRoot: AmperBuildLogsRoot,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val resolvedAndroidRuntimeDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.runtimeClasspath }

        val moduleGradlePath = module.gradlePath(projectRoot)
        val androidModuleData = AndroidModuleData(
            modulePath = moduleGradlePath,
            moduleClasses = listOf(),
            resolvedAndroidRuntimeDependencies = resolvedAndroidRuntimeDependencies.map {
                ResolvedDependency("group", "artifact", "version", it)
            },
        )

        val request = AndroidBuildRequest(
            root = projectRoot.path,
            phase = AndroidBuildRequest.Phase.Prepare,
            modules = setOf(androidModuleData),
            buildTypes = setOf(buildType.toAndroidRequestBuildType),
            sdkDir = androidSdkPath,
            targets = setOf(moduleGradlePath),
        )
        val inputs = listOf((module.source as PotatoModuleFileSource).buildFile.parent.resolve("res").toAbsolutePath())
        val androidConfig = fragments.joinToString { it.settings.android.repr }
        val configuration = mapOf("androidConfig" to androidConfig)
        val result = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            val logFileName = UUID.randomUUID()
            val gradleLogStdoutPath = buildLogsRoot.path / "gradle" / "prepare-$logFileName.stdout"
            val gradleLogStderrPath = buildLogsRoot.path / "gradle" / "prepare-$logFileName.stderr"
            gradleLogStdoutPath.createParentDirectories()
            val result = runAndroidBuild(
                request,
                taskOutputRoot.path / "gradle-project",
                gradleLogStdoutPath,
                gradleLogStderrPath,
                RClassAndroidBuildResult::class.java,
                eventHandler = { it.handle(gradleLogStdoutPath, gradleLogStderrPath) },
            )
            val outputs = result.paths.map { Path(it) }.filter { it.extension.lowercase() == "jar" }
            ExecuteOnChangedInputs.ExecutionResult(outputs)
        }

        return Result(classpath = result.outputs)
    }

    class Result(override val classpath: List<Path>) : TaskResult, AdditionalClasspathProvider
}
