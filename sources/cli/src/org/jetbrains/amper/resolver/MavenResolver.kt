/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenLocalRepository
import org.jetbrains.amper.dependency.resolution.Message
import org.jetbrains.amper.dependency.resolution.ModuleDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.Severity
import org.jetbrains.amper.diagnostics.DoNotLogToTerminalCookie
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.use
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.name

class MavenResolver(private val userCacheRoot: AmperUserCacheRoot) {

    suspend fun resolve(
        coordinates: Collection<String>,
        repositories: Collection<String>,
        scope: ResolutionScope = ResolutionScope.COMPILE,
        platform: ResolutionPlatform = ResolutionPlatform.JVM,
        resolveSourceMoniker: String,
    ): Collection<Path> = spanBuilder("mavenResolve")
        .setAttribute("coordinates", coordinates.joinToString(" "))
        .setAttribute("repositories", repositories.joinToString(" "))
        .setAttribute("scope", scope.toString())
        .setAttribute("platform", platform.toString())
        .also { builder -> platform.nativeTarget?.let { builder.setAttribute("nativeTarget", it) } }
        .startSpan().use { span ->
            val acceptedRepositories = mutableListOf<String>()
            for (url in repositories) {
                @Suppress("HttpUrlsUsage")
                if (url.startsWith("http://")) {
                    // TODO: Special --insecure-http-repositories option or some flag in project.yaml
                    // to acknowledge http:// usage

                    // report only once per `url`
                    if (alreadyReportedHttpRepositories.put(url, true) == null) {
                        logger.warn("http:// repositories are not secure and should not be used: $url")
                    }

                    continue
                }

                if (!url.startsWith("https://")) {

                    // report only once per `url`
                    if (alreadyReportedNonHttpsRepositories.put(url, true) == null) {
                        logger.warn("Non-https repositories are not supported, skipping url: $url")
                    }

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

                // Very ugly hack for AMPER-395 DR: Unable to resolve gradle-tooling-api
                val scope1 = when {
                    coordinates.any { it.startsWith("org.gradle:gradle-tooling-api:") } && scope == ResolutionScope.COMPILE -> ResolutionScope.RUNTIME
                    else -> scope
                }
                this.scope = scope1

                this.platforms = setOf(platform)
                this.downloadSources = false
            }.use { context ->
                val root = ModuleDependencyNode(
                    context,
                    "root",
                    coordinates.map {
                        val (group, module, version) = it.split(":")
                        MavenDependencyNode(context, group, module, version)
                    }
                )
                val resolver = Resolver()
                resolver.buildGraph(root)
                resolver.downloadDependencies(root)

                val files = mutableSetOf<Path>()
                val errorNodes = mutableListOf<DependencyNode>()
                for (node in root.distinctBfsSequence()) {
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
                files
            }
        }

    private val Message.message: String
        get() = "$text ($extra)"

    private val logger = LoggerFactory.getLogger(javaClass)

    private val alreadyReportedHttpRepositories = ConcurrentHashMap<String, Boolean>()
    private val alreadyReportedNonHttpsRepositories = ConcurrentHashMap<String, Boolean>()
}

class MavenResolverException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
