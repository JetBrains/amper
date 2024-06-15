/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.CommonTaskUtils
import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path

class JvmRuntimeClasspathTask(
    override val taskName: TaskName,
    private val module: PotatoModule,
    private val isTest: Boolean,
): Task {
    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val compileTask = dependenciesResult.filterIsInstance<JvmCompileTask.TaskResult>().singleOrNull()
            ?: error("${JvmCompileTask::class.simpleName} result is not found in dependencies")

        val classpath = CommonTaskUtils.buildRuntimeClasspath(compileTask)

        return Result(
            jvmRuntimeClasspath = classpath,
            module = module,
            isTest = isTest,
            dependencies = dependenciesResult,
        )
    }

    class Result(
        val jvmRuntimeClasspath: List<Path>,
        val module: PotatoModule,
        val isTest: Boolean,
        override val dependencies: List<TaskResult>,
    ): TaskResult
}
