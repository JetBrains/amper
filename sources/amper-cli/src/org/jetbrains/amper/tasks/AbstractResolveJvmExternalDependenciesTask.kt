/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.dr.resolver.flow.toRepository
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.incrementalcache.executeForFiles
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import kotlin.io.path.pathString

// TODO merge with regular resolveDependencies task?
//  Hint: Move platform, scope and coordinates out as abstract members.
internal abstract class AbstractResolveJvmExternalDependenciesTask(
    private val module: AmperModule,
    private val userCacheRoot: AmperUserCacheRoot,
    private val incrementalCache: IncrementalCache,
    private val resolutionMonikerPrefix: String,
): Task {
    private val mavenResolver = MavenResolver(userCacheRoot, incrementalCache)

    protected abstract fun getMavenCoordinatesToResolve(): List<String>

    protected open val incrementalCacheKey: String get() = taskName.name
    
    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val repositories = module.mavenRepositories.filter { it.resolve }.map { it.toRepository() }.distinct()
        
        val externalUnscopedCoords = getMavenCoordinatesToResolve()
            .ifEmpty { return Result(emptyList()) }

        val configuration = mapOf(
            "userCacheRoot" to userCacheRoot.path.pathString,
            "repositories" to repositories.joinToString("|"),
            "dependencies-coordinates" to externalUnscopedCoords.joinToString("|"),
        )
        val resolvedExternalJars = incrementalCache.executeForFiles(
            incrementalCacheKey,
            configuration,
            emptyList(),
        ) {
            spanBuilder(taskName.name)
                .setAmperModule(module)
                .setListAttribute("dependencies-coordinates", externalUnscopedCoords)
                .use {
                    mavenResolver.resolve(
                        coordinates = externalUnscopedCoords,
                        repositories = repositories,
                        platform = ResolutionPlatform.JVM,
                        scope = ResolutionScope.RUNTIME,
                        resolveSourceMoniker = "$resolutionMonikerPrefix${module.userReadableName}-${Platform.JVM.pretty}",
                    )
                }
        }

        return Result(resolvedExternalJars)
    }

    class Result(val externalJars: List<Path>) : TaskResult
}
