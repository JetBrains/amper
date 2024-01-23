/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenLocalRepository
import org.jetbrains.amper.dependency.resolution.ModuleDependencyNode
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.Severity
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.use
import java.nio.file.Path

class MavenResolver(private val userCacheRoot: AmperUserCacheRoot) {

    fun resolve(
        coordinates: Collection<String>,
        repositories: Collection<String>,
        scope: ResolutionScope = ResolutionScope.COMPILE,
    ): Collection<Path> = spanBuilder("mavenResolve")
        .setAttribute("coordinates", coordinates.joinToString(" "))
        .startSpan().use {
            val context = Context {
                this.cache = {
                    amperCache = userCacheRoot.path.resolve(".amper")
                    localRepositories = listOf(MavenLocalRepository(userCacheRoot.path.resolve(".m2.cache")))
                }
                this.repositories = repositories.toList()
                this.scope = scope
            }
            val resolver = Resolver(
                ModuleDependencyNode(
                    context,
                    "root",
                    coordinates.map {
                        val (group, module, version) = it.split(":")
                        MavenDependencyNode(context, group, module, version)
                    }
                )
            ).buildGraph().downloadDependencies()

            val files = mutableSetOf<Path>()
            val errors = mutableListOf<String>()
            for (node in resolver.root.asSequence()) {
                if (node is MavenDependencyNode) {
                    node.dependency
                        .files
                        .values
                        .mapNotNull { it.path }
                        .filter { it.toFile().exists() }
                        .forEach { files.add(it) }
                }
                node.messages
                    .filter { it.severity == Severity.ERROR }
                    .map { "${it.text} (${it.extra})" }
                    .toCollection(errors)
            }
            if (errors.isNotEmpty()) {
                throw MavenResolverException(errors.first()).apply {
                    errors.drop(1).map { MavenResolverException(it) }.forEach { addSuppressed(it) }
                }
            }
            return files
        }
}

class MavenResolverException(message: String) : RuntimeException(message)
