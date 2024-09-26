/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenLocalRepository
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.Severity
import org.jetbrains.amper.dependency.resolution.message
import org.jetbrains.amper.diagnostics.DoNotLogToTerminalCookie
import org.jetbrains.amper.frontend.dr.resolver.MavenCoordinates
import org.jetbrains.amper.frontend.dr.resolver.ResolutionDepth
import org.jetbrains.amper.frontend.dr.resolver.mavenCoordinates
import org.jetbrains.amper.frontend.dr.resolver.moduleDependenciesResolver
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
        .useWithScope {
            val context = Context {
                this.cache = getCliDefaultFileCacheBuilder(userCacheRoot)
                this.repositories = repositories
                this.scope = scope
                this.platforms = setOf(platform)
            }

            val root = DependencyNodeHolder(
                name = "root",
                children = coordinates.map {
                    val (group, module, version) = it.split(":")
                    MavenDependencyNode(context, group, module, version)
                }
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
        .useWithScope { span ->
            with(moduleDependenciesResolver) {
                root.resolveDependencies(ResolutionDepth.GRAPH_FULL, downloadSources = false)
            }

            val errorNodes = mutableListOf<DependencyNode>()
            for (node in root.distinctBfsSequence()) {
                if (node.messages.any { it.severity == Severity.ERROR }) {
                    errorNodes.add(node)
                }
            }

            if (errorNodes.isNotEmpty()) {
                val errors = errorNodes.flatMap { it.messages }.filter { it.severity == Severity.ERROR }

                for (error in errors) {
                    span.recordException(error.exception ?: MavenResolverException(error.message))
                    DoNotLogToTerminalCookie.use {
                        logger.error(error.message, error.exception)
                    }
                }

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

fun getCliDefaultFileCacheBuilder(userCacheRoot: AmperUserCacheRoot):  FileCacheBuilder.() -> Unit = {
    amperCache = userCacheRoot.path.resolve(".amper")
    localRepositories = listOf(MavenLocalRepository(userCacheRoot.path.resolve(".m2.cache")))
}

