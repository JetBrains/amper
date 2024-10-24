/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ksp

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import java.nio.file.Path

class KspProcessorClasspathTask(
    override val taskName: TaskName,
    private val module: AmperModule,
    private val isTest: Boolean,
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult = Result(
        processorClasspath = buildRuntimeClasspath(dependenciesResult),
        module = module,
        isTest = isTest,
    )

    private fun buildRuntimeClasspath(dependenciesResult: List<TaskResult>): List<Path> {
        val localProcessorsRuntimeClasspath = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>()
            .flatMap { it.jvmRuntimeClasspath }
        val externalProcessorDependencies =
            dependenciesResult.filterIsInstance<ResolveKspProcessorDependenciesTask.Result>()
                .singleOrNull()
                ?.kspProcessorExternalJars
                ?: error(
                    "${ResolveKspProcessorDependenciesTask::class.simpleName} result is not found in " +
                            "dependencies of ${KspProcessorClasspathTask::class.simpleName}"
                )

        return (localProcessorsRuntimeClasspath + externalProcessorDependencies).distinct()
    }

    class Result(
        val processorClasspath: List<Path>,
        val module: AmperModule,
        val isTest: Boolean,
    ) : TaskResult
}
