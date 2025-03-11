/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.android.extractAars
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.AdditionalClasspathProvider
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path
import kotlin.io.path.div

class TransformAarExternalDependenciesTask(
    override val taskName: TaskName,
    private val executeOnChangedInputs: ExecuteOnChangedInputs
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val resolvedAndroidCompileDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { it.compileClasspath }

        val executionResult = executeOnChangedInputs.execute(taskName.name, mapOf(), resolvedAndroidCompileDependencies) {
                val outputs = resolvedAndroidCompileDependencies.extractAars().map { it / "classes.jar" }
                ExecuteOnChangedInputs.ExecutionResult(outputs, mapOf())
            }
        return Result(executionResult.outputs)
    }

    class Result(override val compileClasspath: List<Path>) : TaskResult, AdditionalClasspathProvider
}
