/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import org.jetbrains.amper.cli.logging.DoNotLogToTerminalCookie
import org.jetbrains.amper.cli.userReadableError
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
import org.jetbrains.amper.frontend.dr.resolver.ResolutionDepth
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.collectBuildProblems
import org.jetbrains.amper.frontend.dr.resolver.emptyContext
import org.jetbrains.amper.frontend.dr.resolver.getAmperFileCacheBuilder
import org.jetbrains.amper.frontend.dr.resolver.mavenCoordinates
import org.jetbrains.amper.frontend.dr.resolver.moduleDependenciesResolver
import org.jetbrains.amper.incrementalcache.CachedPaths
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.renderMessage
import org.jetbrains.amper.tasks.toCachedPaths
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.pathString

class MavenResolver(
    private val userCacheRoot: AmperUserCacheRoot,
    private val incrementalCache: IncrementalCache,
) {
    /**
     * Creates empty DR Context, reusing cache root and incremental cache from the [MavenResolver].
     */
    fun emptyContext(openTelemetry: OpenTelemetry?): Context = emptyContext(userCacheRoot, openTelemetry, incrementalCache)

    suspend fun resolve(
        coordinates: List<String>,
        repositories: List<Repository>,
        scope: ResolutionScope,
        platform: ResolutionPlatform,
        resolveSourceMoniker: String,
    ): CachedPaths = resolveWithContext(repositories, scope, platform, resolveSourceMoniker) {
        RootDependencyNodeWithContext(
            templateContext = this,
            children = coordinates.map {
                val (group, module, version) = it.split(":")
                MavenDependencyNodeWithContext(this, group, module, version, false)
            },
        )
    }.toCachedPaths()

    /**
     * Create a [Context] and resolve dependencies on a passed [root].
     * Also, create respective span.
     */
    suspend fun resolveWithContext(
        repositories: List<Repository>,
        scope: ResolutionScope,
        platform: ResolutionPlatform,
        resolveSourceMoniker: String,
        resolutionDepth: ResolutionDepth = ResolutionDepth.GRAPH_FULL,
        root: Context.() -> RootDependencyNodeWithContext,
    ): ResolvedGraph = spanBuilder("mavenResolve")
        .setAttribute("repositories", repositories.joinToString(" "))
        .setAttribute("user-cache-root", userCacheRoot.path.pathString)
        .setAttribute("scope", scope.name)
        .setAttribute("platform", platform.name)
        .setAttribute("resolutionDepth", resolutionDepth.name)
        .also { builder ->
            platform.nativeTarget?.let { builder.setAttribute("nativeTarget", it) }
            platform.wasmTarget?.let { builder.setAttribute("wasmTarget", it) }
        }
        .use {
            val context = Context {
                this.cache = getAmperFileCacheBuilder(userCacheRoot)
                this.repositories = repositories
                this.scope = scope
                this.platforms = setOf(platform)
                this.openTelemetry = GlobalOpenTelemetry.get()
                this.incrementalCache = this@MavenResolver.incrementalCache
            }

            val root = root(context)

            val resolvedGraph = resolve(root, resolveSourceMoniker, resolutionDepth)
            resolvedGraph
        }

    suspend fun resolve(
        root: RootDependencyNodeWithContext,
        resolveSourceMoniker: String,
        resolutionDepth: ResolutionDepth = ResolutionDepth.GRAPH_FULL,
    ): ResolvedGraph = spanBuilder("mavenResolve")
        .setAttribute("coordinates", root.getExternalDependencies().joinToString(" "))
        .also { builder ->
            root.children.firstOrNull()?.let {
                builder.setAttribute("repositories", it.context.settings.repositories.joinToString(" "))
                it.context.settings.platforms.singleOrNull()?.nativeTarget?.let {
                    builder.setAttribute(
                        "nativeTarget",
                        it
                    )
                }
                it.context.settings.platforms.singleOrNull()?.wasmTarget?.let { builder.setAttribute("wasmTarget", it) }
            }
        }
        .use { span ->
            val resolvedGraph = with(moduleDependenciesResolver) {
                root.resolveDependencies(resolutionDepth, downloadSources = false)
            }

            reportDiagnostics(resolvedGraph.root, span, resolveSourceMoniker)
            resolvedGraph
        }

    private fun reportDiagnostics(root: DependencyNode, span: Span, resolveSourceMoniker: String) {
        val diagnosticsReporter = CollectingProblemReporter()
        collectBuildProblems(root, diagnosticsReporter, Level.Warning)

        val buildProblems = diagnosticsReporter.problems

        for (buildProblem in buildProblems) {
            when (buildProblem.level) {
                Level.Warning -> DoNotLogToTerminalCookie.use {
                    logger.warn(buildProblem.message)
                }

                Level.Error -> {
                    span.recordException(MavenResolverException(buildProblem.message))
                    DoNotLogToTerminalCookie.use {
                        logger.error(buildProblem.message)
                    }
                }

                else -> { /* do nothing */
                }
            }
        }

        val errors = buildProblems.filter { it.level.atLeastAsSevereAs(Level.Error) }
        if (errors.isNotEmpty()) {
            userReadableError(
                "Unable to resolve dependencies for $resolveSourceMoniker:\n\n" +
                        errors.joinToString("\n\n") { renderMessage(it) })
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}

class MavenResolverException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal fun DependencyNode.getExternalDependencies(directOnly: Boolean = false): List<MavenCoordinates> {
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
