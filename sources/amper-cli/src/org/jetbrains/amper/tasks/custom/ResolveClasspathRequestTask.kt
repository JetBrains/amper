/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.JvmClassesJarTask

/**
 * Resolves [TaskFromPluginDescription.ClasspathRequest].
 */
internal class ResolveClasspathRequestTask(
    override val taskName: TaskName,
    private val classpathRequest: TaskFromPluginDescription.ClasspathRequest,
) : Task {

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
        val resolvedFiles = buildList {
            dependenciesResult.forEach { result ->
                when(result) {
                    is JvmClassesJarTask.Result -> add(result.jarPath)
                    is ResolveCustomExternalDependenciesTask.Result -> addAll(result.resolvedFiles)
                    else -> error("Unexpected dependency: $result")
                }
            }
        }

        // Complete resolution
        classpathRequest.node.resolvedFiles = resolvedFiles

        return EmptyTaskResult
    }
}