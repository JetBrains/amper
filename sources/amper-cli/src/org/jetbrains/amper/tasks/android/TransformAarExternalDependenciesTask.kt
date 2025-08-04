/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.amper.core.extract.extractFileToLocation
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.AdditionalClasspathProvider
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.jvm.RuntimeClasspathElementProvider
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class TransformAarExternalDependenciesTask(
    override val taskName: TaskName,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val classpathExtractor: (ResolveExternalDependenciesTask.Result) -> List<Path> = { it.compileClasspath },
) : Task {
    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val resolvedAndroidCompileDependencies = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .flatMap { classpathExtractor(it) }

        val executionResult = executeOnChangedInputs.execute(taskName.name, mapOf(), resolvedAndroidCompileDependencies) {
            if (resolvedAndroidCompileDependencies.isNotEmpty()) {
                logger.info("Transforming AAR external dependencies...")
            }
            val outputs = resolvedAndroidCompileDependencies.extractAars().map { it / "classes.jar" }
            ExecuteOnChangedInputs.ExecutionResult(outputs, mapOf())
        }
        return Result(executionResult.outputs, executionResult.outputs)
    }

    class Result(
        override val compileClasspath: List<Path>,
        override val paths: List<Path>,
    ) : TaskResult, AdditionalClasspathProvider, RuntimeClasspathElementProvider
}

private suspend fun List<Path>.extractAars(): List<Path> = coroutineScope {
    filter { it.extension == "aar" }
        .map {
            async {
                val targetFolder = it.parent / it.nameWithoutExtension
                extractFileToLocation(it, targetFolder)
                targetFolder
            }
        }.awaitAll()
}
