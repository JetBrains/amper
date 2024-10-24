/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path

class JvmRuntimeClasspathTask(
    override val taskName: TaskName,
    private val module: AmperModule,
    private val isTest: Boolean,
): Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val classpath = buildRuntimeClasspath(dependenciesResult)

        return Result(
            jvmRuntimeClasspath = classpath,
            module = module,
            isTest = isTest,
        )
    }

    // TODO this not how classpath should be built, it does not preserve order
    //  also will fail on conflicting dependencies
    //  also it depends on task hierarchy, which could be different from classpath
    //  but for demo it's fine
    //  I suggest to return to this task after our own dependency resolution engine
    private fun buildRuntimeClasspath(dependenciesResult: List<TaskResult>): List<Path> {
        val classpathElements = dependenciesResult.filterIsInstance<RuntimeClasspathElementProvider>()
        check(classpathElements.isNotEmpty()) {
            "No ${RuntimeClasspathElementProvider::class.simpleName} results are found in dependencies"
        }

        val dependenciesTask = dependenciesResult.filterIsInstance<ResolveExternalDependenciesTask.Result>().singleOrNull()
            ?: error("${ResolveExternalDependenciesTask::class.simpleName} result is not found in dependencies")

        val addToClasspath = classpathElements.flatMap { it.paths } +
                dependenciesTask.runtimeClasspath

        return addToClasspath.distinct()
    }

    class Result(
        val jvmRuntimeClasspath: List<Path>,
        val module: AmperModule,
        val isTest: Boolean,
    ): TaskResult
}
