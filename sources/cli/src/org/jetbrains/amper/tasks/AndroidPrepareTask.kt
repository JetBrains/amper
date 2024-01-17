/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import AndroidBuildRequest
import AndroidModuleData
import RClassAndroidBuildResult
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.repr
import org.jetbrains.amper.util.toAndroidRequestBuildType
import runAndroidBuild
import java.nio.file.Path

class AndroidPrepareTask(
    private val module: PotatoModule,
    private val buildType: BuildType,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val fragments: List<Fragment>,
    override val taskName: TaskName
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val rootPath =
            (module.source as? PotatoModuleFileSource)?.buildFile?.parent ?: error("No build file ${module.source}")
        val request = AndroidBuildRequest(
            rootPath,
            AndroidBuildRequest.Phase.Prepare,
            setOf(AndroidModuleData(":")),
            setOf(buildType.toAndroidRequestBuildType)
        )
        val inputs = listOf((module.source as PotatoModuleFileSource).buildFile.parent.resolve("res"))
        val androidConfig = fragments.joinToString { it.settings.android.repr }
        val configuration = mapOf("androidConfig" to androidConfig)
        val result = executeOnChangedInputs.execute(taskName.name, configuration, inputs) {
            val result = runAndroidBuild<RClassAndroidBuildResult>(
                request,
                sourcesPath = Path.of("../../").toAbsolutePath().normalize()
            )
            ExecuteOnChangedInputs.ExecutionResult(result.paths.map { Path.of(it) })
        }

        return JvmCompileTask.AdditionalClasspathProviderTaskResult(dependenciesResult, result.outputs)
    }
}
