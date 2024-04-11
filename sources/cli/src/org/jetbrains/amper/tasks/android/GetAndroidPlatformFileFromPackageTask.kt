/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.cli.downloadAndExtractAndroidPlatform
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import java.nio.file.Path
import kotlin.io.path.createDirectories

class GetAndroidPlatformFileFromPackageTask(
    private val packageName: String,
    private val androidSdkPath: Path,
    override val taskName: TaskName,
) : Task {
    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        androidSdkPath.createDirectories()
        val outputs = listOf(downloadAndExtractAndroidPlatform(packageName, androidSdkPath))
        return TaskResult(dependenciesResult, outputs)
    }

    data class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val outputs: List<Path>
    ) : org.jetbrains.amper.tasks.TaskResult
}
