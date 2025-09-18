/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.AbstractResolveJvmExternalDependenciesTask
import org.jetbrains.amper.tasks.TaskResult
import java.nio.file.Path
import java.security.MessageDigest

internal class ResolveCustomExternalDependenciesTask(
    override val taskName: TaskName,
    module: AmperModule,
    incrementalCache: IncrementalCache,
    private val destination: TaskFromPluginDescription.ClasspathRequest,
    userCacheRoot: AmperUserCacheRoot,
) : AbstractResolveJvmExternalDependenciesTask(
    module,
    userCacheRoot,
    incrementalCache,
    resolutionMonikerPrefix = "custom external dependencies: ",
) {
    override val incrementalCacheKey: String = externalDependenciesHash(destination.externalDependencies)

    override fun getMavenCoordinatesToResolve(): List<String> {
        return destination.externalDependencies
    }

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
        val result = super.run(dependenciesResult, executionContext)
                as AbstractResolveJvmExternalDependenciesTask.Result
        return Result(
            resolvedFiles = result.externalJars,
            destination = destination,
        )
    }

    class Result(
        val resolvedFiles: List<Path>,
        val destination: TaskFromPluginDescription.ClasspathRequest,
    ) : TaskResult
}

private fun externalDependenciesHash(coordinates: List<String>): String {
    return MessageDigest.getInstance("SHA512")
        .digest(coordinates.toSortedSet().joinToString("|").toByteArray())
        .joinToString("") { "%02x".format(it) }
}