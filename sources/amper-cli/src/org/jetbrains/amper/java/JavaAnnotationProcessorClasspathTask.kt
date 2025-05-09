/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.java

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import java.nio.file.Path

class JavaAnnotationProcessorClasspathTask(override val taskName: TaskName) : Task {
    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext
    ): TaskResult = Result(
        processorClasspath = buildRuntimeClasspath(dependenciesResult),
    )

    private fun buildRuntimeClasspath(dependenciesResult: List<TaskResult>): List<Path> {
        val localProcessorsRuntimeClasspath = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>()
            .flatMap { it.jvmRuntimeClasspath }
        val externalProcessorDependencies =
            dependenciesResult.filterIsInstance<ResolveJavaAnnotationProcessorDependenciesTask.Result>()
                .singleOrNull()
                ?.javaAnnotationProcessorExternalJars
                ?: error(
                    "${ResolveJavaAnnotationProcessorDependenciesTask::class.simpleName} result is not found in " +
                            "dependencies of ${JavaAnnotationProcessorClasspathTask::class.simpleName}"
                )

        return (localProcessorsRuntimeClasspath + externalProcessorDependencies).distinct()
    }

    class Result(
        val processorClasspath: List<Path>,
    ) : TaskResult
}