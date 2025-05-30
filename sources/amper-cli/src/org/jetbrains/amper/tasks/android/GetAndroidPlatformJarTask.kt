/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.AdditionalClasspathProvider
import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path

class GetAndroidPlatformJarTask(
    private val getAndroidPlatformFileFromPackageTask: GetAndroidPlatformFileFromPackageTask
) : Task {
    override val taskName: TaskName
        get() = getAndroidPlatformFileFromPackageTask.taskName

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val result = getAndroidPlatformFileFromPackageTask.run(dependenciesResult, executionContext)
        val classpath = result.outputs.map { it.resolve("android.jar") }
        return Result(classpath)
    }

    class Result(override val compileClasspath: List<Path>) : TaskResult, AdditionalClasspathProvider
}
