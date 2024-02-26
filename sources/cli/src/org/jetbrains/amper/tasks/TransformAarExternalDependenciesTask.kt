/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.core.extract.extractFileToLocation
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.tasks.JvmCompileTask.AdditionalClasspathProviderTaskResult
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class TransformAarExternalDependenciesTask(
    override val taskName: TaskName,
    private val executeOnChangedInputs: ExecuteOnChangedInputs
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val resolvedAndroidRuntimeDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
            .flatMap { it.compileClasspath }

        val executionResult = executeOnChangedInputs.execute(taskName.name, mapOf(), resolvedAndroidRuntimeDependencies) {
                val outputs = resolvedAndroidRuntimeDependencies.map {
                    if (it.extension == "aar") {
                        val targetFolder = it.parent / it.nameWithoutExtension
                        extractFileToLocation(it, targetFolder)
                        targetFolder / "classes.jar"
                    } else it
                }
                ExecuteOnChangedInputs.ExecutionResult(outputs, mapOf())
            }
        return AdditionalClasspathProviderTaskResult(dependenciesResult, executionResult.outputs)
    }
}