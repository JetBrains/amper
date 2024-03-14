/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.jetbrains.amper.dependency.resolution.extractAars
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmCompileTask.AdditionalClasspathProviderTaskResult
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import kotlin.io.path.div

class TransformAarExternalDependenciesTask(
    override val taskName: TaskName,
    private val executeOnChangedInputs: ExecuteOnChangedInputs
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val resolvedAndroidCompileDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
            .flatMap { it.compileClasspath }

        val executionResult = executeOnChangedInputs.execute(taskName.name, mapOf(), resolvedAndroidCompileDependencies) {
                val outputs = resolvedAndroidCompileDependencies.extractAars().map { it / "classes.jar" }
                ExecuteOnChangedInputs.ExecutionResult(outputs, mapOf())
            }
        return AdditionalClasspathProviderTaskResult(dependenciesResult, executionResult.outputs)
    }
}
