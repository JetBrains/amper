/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import io.opentelemetry.api.trace.Span
import org.jetbrains.amper.cli.logging.DoNotLogToTerminalCookie
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NoOpCollectingProblemReporter
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.diagnostics.WithThrowable
import org.jetbrains.amper.frontend.dr.resolver.ResolutionDepth
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.collectBuildProblems
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters.DependencyBuildProblem
import org.jetbrains.amper.frontend.dr.resolver.getAmperFileCacheBuilder
import org.jetbrains.amper.frontend.dr.resolver.mavenCoordinates
import org.jetbrains.amper.frontend.dr.resolver.moduleDependenciesResolver
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.pathString

class MavenResolver(private val userCacheRoot: AmperUserCacheRoot) {

    suspend fun resolve(
        coordinates: List<String>,
        repositories: List<Repository>,
        scope: ResolutionScope,
        platform: ResolutionPlatform,
        resolveSourceMoniker: String,
    ): List<Path> = spanBuilder("mavenResolve")
        .setAttribute("coordinates", coordinates.joinToString(" "))
        .setAttribute("repositories", repositories.joinToString(" "))
        .setAttribute("user-cache-root", userCacheRoot.path.pathString)
        .setAttribute("scope", scope.name)
        .setAttribute("platform", platform.name)
        .also { builder -> platform.nativeTarget?.let { builder.setAttribute("nativeTarget", it) } }
        .use {
            val context = Context {
                this.cache = getAmperFileCacheBuilder(userCacheRoot)
                this.repositories = repositories
                this.scope = scope
                this.platforms = setOf(platform)
            }

            val root = DependencyNodeHolder(
                name = "root",
                children = coordinates.map {
                    val (group, module, version) = it.split(":")
                    MavenDependencyNode(context, group, module, version, false)
                },
                context
            )

            resolve(root, resolveSourceMoniker)

            val files = root.dependencyPaths()
            files
        }

    suspend fun resolve(
        root: DependencyNodeHolder,
        resolveSourceMoniker: String,
    ) = spanBuilder("mavenResolve")
        .setAttribute("coordinates", root.getExternalDependencies().joinToString(" "))
        .also { builder -> root.children.firstOrNull()?.let{
            builder.setAttribute("repositories", it.context.settings.repositories.joinToString(" "))
            it.context.settings.platforms.singleOrNull()?.nativeTarget?.let { builder.setAttribute("nativeTarget", it) }
        }}
        .use { span ->
            with(moduleDependenciesResolver) {
                root.resolveDependencies(ResolutionDepth.GRAPH_FULL, downloadSources = false)
            }

            reportDiagnostics(root, span, resolveSourceMoniker)
        }

    private fun reportDiagnostics(root: DependencyNodeHolder, span: Span, resolveSourceMoniker: String) {
        val diagnosticsReporter = NoOpCollectingProblemReporter()
        collectBuildProblems(root, diagnosticsReporter, Level.Warning)

        val buildProblems = diagnosticsReporter.getProblems()

        for (buildProblem in buildProblems) {
            when (buildProblem.level) {
                Level.Warning -> DoNotLogToTerminalCookie.use {
                    logger.warn(buildProblem.message)
                }

                Level.Error -> {
                    var throwable: Throwable? = null
                    if (buildProblem is DependencyBuildProblem) {
                        val errorMessage = buildProblem.errorMessage
                        if (errorMessage is WithThrowable) {
                            throwable = errorMessage.throwable
                        }
                    }

                    span.recordException(throwable ?: MavenResolverException(buildProblem.message))
                    DoNotLogToTerminalCookie.use {
                        logger.error(buildProblem.message, throwable)
                    }
                }

                else -> { /* do nothing */ }
            }
        }

        val errors = buildProblems.filter { it.level == Level.Error }
        if (errors.isNotEmpty()) {
            userReadableError(
                "Unable to resolve dependencies for $resolveSourceMoniker:\n\n" +
                        errors.joinToString("\n") { it.message })
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
            it.fillExternalDependencies(dependenciesList, directOnly)
        }
    }
}
