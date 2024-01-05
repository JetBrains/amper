/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.MavenCacheDirectory
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ModuleDependencyNode
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.Severity
import org.jetbrains.amper.dependency.resolution.createFor
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.use
import java.nio.file.Path

class MavenResolver(private val userCacheRoot: AmperUserCacheRoot) {

    fun resolve(coordinates: Collection<String>): Collection<Path> = spanBuilder("mavenResolve")
        .setAttribute("coordinates", coordinates.joinToString(" "))
        .startSpan().use {
            val resolver = Resolver.createFor({ resolver ->
                ModuleDependencyNode(
                    "root",
                    coordinates.map {
                        val (group, module, version) = it.split(":")
                        MavenDependencyNode(resolver, group, module, version)
                    }
                )
            }) {
                cache = listOf(MavenCacheDirectory(userCacheRoot.path.resolve(".m2.cache")))
            }.buildGraph().downloadDependencies()

            val files = mutableSetOf<Path>()
            val errors = mutableListOf<String>()
            for (node in resolver.root.asSequence()) {
                if (node is MavenDependencyNode) {
                    node.dependency.jar.path?.takeIf { it.toFile().exists() }?.let { files.add(it) }
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
