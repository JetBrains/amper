/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path

/**
 * Dummy task only needed as classpath provider because hot-reload requires to depend on classes instead of jars.
 * 
 * TODO: it seems like firework supports hotswap from jars, we probably need to utilize that instead  
 */
class JvmClassesTask(override val taskName: TaskName): Task {
    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val compileTaskResults = dependenciesResult.filterIsInstance<JvmCompileTask.Result>()
        require(compileTaskResults.isNotEmpty()) {
            "Call classes task without any compilation dependency"
        }
        
        return Result(compileTaskResults.flatMap { it.paths })
    }

    class Result(override val paths: List<Path>): TaskResult, RuntimeClasspathElementProvider
}
