/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.downloadAndExtractAndroidPlatform
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.util.ExecuteOnChangedInputs

class GetAndroidPlatformJarTask(
    private val platformCode: String,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    override val taskName: TaskName
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val result = executeOnChangedInputs.execute(taskName.name, mapOf("platformCode" to platformCode), listOf()) {
            ExecuteOnChangedInputs.ExecutionResult(listOf(downloadAndExtractAndroidPlatform(platformCode).resolve("android.jar")))
        }
        return JvmCompileTask.AdditionalClasspathProviderTaskResult(dependenciesResult, result.outputs)
    }
}
