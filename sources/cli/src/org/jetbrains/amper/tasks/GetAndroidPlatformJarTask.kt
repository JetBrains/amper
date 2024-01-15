/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.downloadAndExtractAndroidPlatform
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName

class GetAndroidPlatformJarTask(private val platformCode: String, override val taskName: TaskName) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        return JvmCompileTask.AdditionalClasspathProviderTaskResult(
            dependenciesResult,
            listOf(downloadAndExtractAndroidPlatform(platformCode).resolve("android.jar"))
        )
    }
}
