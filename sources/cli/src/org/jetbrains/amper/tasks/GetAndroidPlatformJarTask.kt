/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName

class GetAndroidPlatformJarTask(
    private val getAndroidPlatformFileFromPackageTask: GetAndroidPlatformFileFromPackageTask
) :
    Task {
    override val taskName: TaskName
        get() = getAndroidPlatformFileFromPackageTask.taskName

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val result = getAndroidPlatformFileFromPackageTask
            .run(dependenciesResult) as GetAndroidPlatformFileFromPackageTask.TaskResult
        val classpath = result.outputs.map { it.resolve("android.jar") }
        return JvmCompileTask.AdditionalClasspathProviderTaskResult(dependenciesResult, classpath)
    }
}