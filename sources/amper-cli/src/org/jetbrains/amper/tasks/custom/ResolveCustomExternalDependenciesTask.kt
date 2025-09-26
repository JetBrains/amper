/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.dr.resolver.flow.toRepository
import org.jetbrains.amper.frontend.dr.resolver.getAmperFileCacheBuilder
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.incrementalcache.executeForFiles
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.resolver.getExternalDependencies
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.buildDependenciesGraph
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import kotlin.io.path.pathString

internal class ResolveCustomExternalDependenciesTask(
    override val taskName: TaskName,
    private val userCacheRoot: AmperUserCacheRoot,
    private val module: AmperModule,
    private val incrementalCache: IncrementalCache,
    private val resolutionScope: ResolutionScope,
    private val externalDependencies: List<String>,
    private val localDependencies: List<AmperModule>,
) : Task {
    private val mavenResolver = MavenResolver(userCacheRoot)

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): Result {
        val repositories = module.mavenRepositories.filter { it.resolve }.map { it.toRepository() }
        val drContext = Context {
            this.repositories = repositories
            this.cache = getAmperFileCacheBuilder(userCacheRoot)
            this.scope = resolutionScope
            this.platforms = setOf(ResolutionPlatform.JVM)
        }
        val externalDependencyNodes = externalDependencies.map {
            val (group, module, version) = it.split(":") // FIXME: This has to be done in frontend
            MavenDependencyNode(drContext, group, module, version, isBom = false)
        }
        val localDependencyNodes = localDependencies.map {
            it.buildDependenciesGraph(isTest = false, Platform.JVM, resolutionScope, userCacheRoot)
        }

        val root = DependencyNodeHolder(
            name = "root",
            children = localDependencyNodes + externalDependencyNodes,
            templateContext = drContext,
        )

        val externalDependencies = root.getExternalDependencies()
        val dependencyPaths = incrementalCache.executeForFiles(
            key = taskName.name,
            inputValues = mapOf(
                "userCacheRoot" to userCacheRoot.path.pathString,
                "repositories" to repositories.joinToString("|"),
                "resolveScope" to resolutionScope.name,
                "dependencies" to externalDependencies.joinToString("|"),
            ),
            inputFiles = emptyList()
        ) {
            spanBuilder(taskName.name)
                .setAmperModule(module)
                .setListAttribute("dependencies-coordinates", externalDependencies.map { it.toString() })
                .use {
                    mavenResolver.resolve(root, "custom external dependencies")
                    root.dependencyPaths()
                }
        }

        return Result(
            resolvedFiles = dependencyPaths,
        )
    }

    class Result(
        val resolvedFiles: List<Path>,
    ) : TaskResult
}