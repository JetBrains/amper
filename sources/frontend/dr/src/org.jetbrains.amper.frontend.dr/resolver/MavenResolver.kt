/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.ResolvedGraph
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeWithContext
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.collectBuildProblems
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.telemetry.use
import kotlin.io.path.pathString

/**
 * An adapter around [ModuleDependenciesResolver] that creates root node, 
 * configures its context and span properly, before performing actual resolve.
 */
open class MavenResolver(
    private val userCacheRoot: AmperUserCacheRoot,
    private val incrementalCache: IncrementalCache,
) {
    /**
     * Creates empty DR Context, reusing cache root and incremental cache from the [MavenResolver].
     */
    fun emptyContext(openTelemetry: OpenTelemetry? = GlobalOpenTelemetry.get()): Context =
        emptyContext(userCacheRoot, openTelemetry, incrementalCache)

    /**
     * Perform resolution over a set of maven coordinates.
     */
    suspend fun resolve(
        coordinates: List<String>,
        repositories: List<Repository>,
        scope: ResolutionScope,
        platform: ResolutionPlatform,
        resolveSourceMoniker: String,
    ): ResolvedGraph = resolveWithContext(repositories, scope, platform, resolveSourceMoniker) { context ->
        RootDependencyNodeWithContext(
            templateContext = context,
            children = coordinates.map {
                val (group, module, version) = it.split(":")
                MavenDependencyNodeWithContext(context, group, module, version, false)
            },
        )
    }

    /**
     * Create a [Context] and resolve dependencies on a passed [rootBuilder].
     * Also, create respective span.
     */
    suspend fun resolveWithContext(
        repositories: List<Repository>,
        scope: ResolutionScope,
        platform: ResolutionPlatform,
        resolveSourceMoniker: String,
        resolutionDepth: ResolutionDepth = ResolutionDepth.GRAPH_FULL,
        rootBuilder: (Context) -> RootDependencyNodeWithContext,
    ): ResolvedGraph = spanBuilder("mavenResolve")
        .setAttribute("repositories", repositories.joinToString(" "))
        .setAttribute("user-cache-root", userCacheRoot.path.pathString)
        .setAttribute("scope", scope.name)
        .setAttribute("platform", platform.name)
        .setAttribute("resolutionDepth", resolutionDepth.name)
        .apply { setAttribute("nativeTarget", platform.nativeTarget ?: return@apply) }
        .apply { setAttribute("wasmTarget", platform.wasmTarget ?: return@apply) }
        .use {
            val context = Context {
                this.cache = getAmperFileCacheBuilder(userCacheRoot)
                this.repositories = repositories
                this.scope = scope
                this.platforms = setOf(platform)
                this.openTelemetry = GlobalOpenTelemetry.get()
                this.incrementalCache = this@MavenResolver.incrementalCache
            }
            resolve(rootBuilder(context), resolveSourceMoniker, resolutionDepth)
        }

    /**
     * Perform a resolution over a passed [root]. 
     */
    suspend fun resolve(
        root: RootDependencyNodeWithContext,
        resolveSourceMoniker: String,
        resolutionDepth: ResolutionDepth = ResolutionDepth.GRAPH_FULL,
    ): ResolvedGraph = spanBuilder("mavenResolve")
        .setAttribute("coordinates", root.getExternalDependencies().joinToString(" "))
        .apply {
            val settings = root.children.firstOrNull()?.context?.settings ?: root.context.settings
            setAttribute("repositories", settings.repositories.joinToString(" "))
            settings.platforms.singleOrNull()?.nativeTarget?.let { setAttribute("nativeTarget", it) }
            settings.platforms.singleOrNull()?.wasmTarget?.let { setAttribute("wasmTarget", it) }
        }
        .use { span ->
            with(moduleDependenciesResolver) { root.resolveDependencies(resolutionDepth, downloadSources = false) }
                .also {
                    // We are referencing the same root, but after [resolveDependencies] was called, so it is filled now.
                    val reporter = CollectingProblemReporter().also { collectBuildProblems(root, it, Level.Warning) }
                    processProblems(reporter.problems, span, resolveSourceMoniker)
                }
        }

    protected open fun processProblems(buildProblems: List<BuildProblem>, span: Span, resolveSourceMoniker: String) =
        Unit
}

class MavenResolverException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun ResolvedGraph.toIncrementalCacheResult() =
    IncrementalCache.ExecutionResult(outputFiles = root.dependencyPaths(), expirationTime = expirationTime)

fun DependencyNode.getExternalDependencies(directOnly: Boolean = false): List<MavenCoordinates> {
    val uniqueDependencies = buildSet { fillExternalDependencies(this, directOnly) }
    return uniqueDependencies.sortedBy { it.toString() }
}

private fun DependencyNode.fillExternalDependencies(
    dependenciesList: MutableSet<MavenCoordinates>,
    directOnly: Boolean = false,
) {
    children.forEach {
        // There can be all sorts of wrapper types here, which are somewhat internal to the dependency resolution module.
        // We only want to add external maven dependencies here anyway, or recurse, so let's not enumerate.
        if (it is MavenDependencyNode) {
            dependenciesList.add(it.mavenCoordinates())
        } else if (!directOnly) {
            it.fillExternalDependencies(dependenciesList, directOnly = false)
        }
    }
}
