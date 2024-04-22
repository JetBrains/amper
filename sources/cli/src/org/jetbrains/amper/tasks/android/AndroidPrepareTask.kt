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
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmCompileTask
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.repr
import org.jetbrains.amper.util.toAndroidRequestBuildType
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension

class AndroidPrepareTask(
    override val taskName: TaskName,
    private val module: PotatoModule,
    private val buildType: BuildType,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val androidSdkPath: Path,
    private val fragments: List<Fragment>,
    private val taskOutputRoot: TaskOutputRoot,
    private val buildLogsRoot: AmperBuildLogsRoot,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val rootPath =
            (module.source as? PotatoModuleFileSource)?.buildFile?.parent ?: error("No build file ${module.source}")

        val resolvedAndroidRuntimeDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
            .flatMap { it.runtimeClasspath }
        val androidModuleData = AndroidModuleData(":", listOf(), resolvedAndroidRuntimeDependencies.map {
            ResolvedDependency("group", "artifact", "version", it)
        })

        val request = AndroidBuildRequest(
            rootPath,
            AndroidBuildRequest.Phase.Prepare,
            setOf(androidModuleData),
            setOf(buildType.toAndroidRequestBuildType),
            sdkDir = androidSdkPath
        )
        val inputs = listOf((module.source as PotatoModuleFileSource).buildFile.parent.resolve("res").toAbsolutePath())
        val androidConfig = fragments.joinToString { it.settings.android.repr }
        val configuration = mapOf("androidConfig" to androidConfig)
        val result = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            val logFileName = UUID.randomUUID()
            val gradleLogStdoutPath = buildLogsRoot.path / "gradle" / "prepare-$logFileName.stdout"
            val gradleLogStderrPath = buildLogsRoot.path / "gradle" / "prepare-$logFileName.stderr"
            gradleLogStdoutPath.createParentDirectories()
            val result = runAndroidBuild<RClassAndroidBuildResult>(
                request,
                taskOutputRoot.path / "gradle-project",
                gradleLogStdoutPath,
                gradleLogStderrPath,
                eventHandler = { it.handle(gradleLogStdoutPath, gradleLogStderrPath) },
            )
            val outputs = result.paths.map { Path.of(it) }.filter { it.extension.lowercase() == "jar" }
            ExecuteOnChangedInputs.ExecutionResult(outputs)
        }

        return JvmCompileTask.AdditionalClasspathProviderTaskResult(dependenciesResult, result.outputs)
    }
}
