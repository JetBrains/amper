/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import java.nio.file.Path

class GetAndroidPlatformFileFromPackageTask(
    private val packageName: String,
    private val androidSdkPath: Path,
    private val userCacheRoot: AmperUserCacheRoot,
    override val taskName: TaskName,
) : Task {
    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        val packagePath = SdkInstallManager(userCacheRoot, androidSdkPath).install(packageName).path
        val localFileSystemPackagePath = packagePath
            .split(";")
            .fold(androidSdkPath) { path, component -> path.resolve(component) }
        return TaskResult(dependenciesResult, listOf(localFileSystemPackagePath))
    }

    data class TaskResult(
        override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
        val outputs: List<Path>
    ) : org.jetbrains.amper.tasks.TaskResult
}
