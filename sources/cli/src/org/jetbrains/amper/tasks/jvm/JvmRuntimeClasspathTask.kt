/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path

class JvmRuntimeClasspathTask(
    override val taskName: TaskName,
    private val module: PotatoModule,
    private val isTest: Boolean,
): Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val compileTask = dependenciesResult.filterIsInstance<JvmCompileTask.Result>().singleOrNull()
            ?: error("${JvmCompileTask::class.simpleName} result is not found in dependencies")

        val classpath = buildRuntimeClasspath(compileTask)

        return Result(
            jvmRuntimeClasspath = classpath,
            module = module,
            isTest = isTest,
            dependencies = dependenciesResult,
        )
    }

    // TODO this not how classpath should be built, it does not preserve order
    //  also will fail on conflicting dependencies
    //  also it depends on task hierarchy, which could be different from classpath
    //  but for demo it's fine
    //  I suggest to return to this task after our own dependency resolution engine
    private fun buildRuntimeClasspath(compileTaskResult: JvmCompileTask.Result): List<Path> {
        val result = mutableListOf<Path>()
        buildRuntimeClasspath(compileTaskResult, result)
        return result
    }

    private fun buildRuntimeClasspath(compileTaskResult: JvmCompileTask.Result, result: MutableList<Path>) {
        val externalClasspath =
            compileTaskResult.dependencies.filterIsInstance<ResolveExternalDependenciesTask.Result>()
                .flatMap { it.runtimeClasspath }
        for (path in externalClasspath) {
            if (!result.contains(path)) {
                result.add(path)
            }
        }

        for (depCompileResult in compileTaskResult.dependencies.filterIsInstance<JvmCompileTask.Result>()) {
            buildRuntimeClasspath(depCompileResult, result)
        }

        result.add(compileTaskResult.classesOutputRoot)
    }

    class Result(
        val jvmRuntimeClasspath: List<Path>,
        val module: PotatoModule,
        val isTest: Boolean,
        override val dependencies: List<TaskResult>,
    ): TaskResult
}
