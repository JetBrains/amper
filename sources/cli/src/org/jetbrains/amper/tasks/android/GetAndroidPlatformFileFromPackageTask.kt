/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.cli.downloadAndExtractAndroidPlatform
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path

class GetAndroidPlatformFileFromPackageTask(
    private val packageName: String,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val androidSdkPath: Path,
    override val taskName: TaskName,
) : Task {
    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        val result = executeOnChangedInputs.execute(taskName.name, mapOf("packageName" to packageName), listOf()) {
            ExecuteOnChangedInputs.ExecutionResult(listOf(downloadAndExtractAndroidPlatform(packageName, androidSdkPath)))
        }
        return TaskResult(dependenciesResult, result.outputs)
    }

    data class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val outputs: List<Path>
    ) : org.jetbrains.amper.tasks.TaskResult
}
