/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenLocalRepository
import org.jetbrains.amper.dependency.resolution.Message
import org.jetbrains.amper.dependency.resolution.ModuleDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.Severity
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.use
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

class MavenResolver(private val userCacheRoot: AmperUserCacheRoot) {

    suspend fun resolve(
        coordinates: Collection<String>,
        repositories: Collection<String>,
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: String = "jvm",
        nativeTarget: String? = null,
    ): Collection<Path> = spanBuilder("mavenResolve")
        .setAttribute("coordinates", coordinates.joinToString(" "))
        .startSpan().use {
            val acceptedRepositories = mutableListOf<String>()
            for (url in repositories) {
                @Suppress("HttpUrlsUsage")
                if (url.startsWith("http://")) {
                    // TODO: Special --insecure-http-repositories option or some flag in project.yaml
                    // to acknowledge http:// usage
                    logger.warn("http:// repositories are not secure and should not be used: $url")
                    continue
                }

                if (!url.startsWith("https://")) {
                    logger.warn("Non-https repositories are not supported, skipping url: $url")
                    continue
                }

                acceptedRepositories.add(url)
            }

            return Context {
                this.cache = {
                    amperCache = userCacheRoot.path.resolve(".amper")
                    localRepositories = listOf(MavenLocalRepository(userCacheRoot.path.resolve(".m2.cache")))
                }
                this.repositories = acceptedRepositories.toList()
                this.scope = scope
                this.platform = platform
                this.nativeTarget = nativeTarget
            }.use { context ->
                val resolver = Resolver(
                    ModuleDependencyNode(
                        context,
                        "root",
                        coordinates.map {
                            val (group, module, version) = it.split(":")
                            MavenDependencyNode(context, group, module, version)
                        }
                    )
                )
                resolver.buildGraph()
                resolver.downloadDependencies()

                val files = mutableSetOf<Path>()
                val errors = mutableListOf<Message>()
                for (node in resolver.root.asSequence()) {
                    if (node is MavenDependencyNode) {
                        node.dependency
                            .files
                            .mapNotNull { it.getPath() }
                            .filterNot { it.name.endsWith("-sources.jar") || it.name.endsWith("-javadoc.jar") }
                            .forEach { file ->
                                check(file.exists()) {
                                    "File '$file' was returned from dependency resolution, but is missing on disk"
                                }
                                files.add(file)
                            }
                    }
                    node.messages
                        .filter { it.severity == Severity.ERROR }
                        .toCollection(errors)
                }
                if (errors.isNotEmpty()) {
                    val first = errors.first()
                    throw MavenResolverException(first.message, first.exception).apply {
                        errors.drop(1).map {
                            MavenResolverException(it.message, it.exception)
                        }.forEach { addSuppressed(it) }
                    }
                }
                files
            }
        }

    private val Message.message: String
        get() = "$text ($extra)"

    private val logger = LoggerFactory.getLogger(javaClass)
}

class MavenResolverException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
