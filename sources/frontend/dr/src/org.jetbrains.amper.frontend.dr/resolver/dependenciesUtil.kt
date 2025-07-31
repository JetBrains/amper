/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.SpanBuilderSource
import org.jetbrains.amper.dependency.resolution.getDefaultFileCacheBuilder
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.problems.reporting.MessageBundle
import java.nio.file.InvalidPathException
import java.security.MessageDigest
import kotlin.io.path.Path

object FrontendDrBundle : MessageBundle("messages.FrontendDrBundle")

val DependencyNode.fragmentDependencies: List<DirectFragmentDependencyNode>
    get() = findParents<DirectFragmentDependencyNode>()

private inline fun <reified T : DependencyNode> DependencyNode.findParents(): List<T> {
    val result = mutableSetOf<T>()
    findParentsImpl(T::class.java, result = result)
    return result.toList()
}

private fun <T : DependencyNode> DependencyNode.findParentsImpl(
    kClass: Class<T>,
    visited: MutableSet<DependencyNode> = mutableSetOf(),
    result: MutableSet<T> = mutableSetOf()
) {
    if (!visited.add(this)) {
        return
    }

    if (kClass.isInstance(this)) {
        result.add(kClass.cast(this))
    } else {
        parents.forEach { it.findParentsImpl(kClass, visited = visited, result = result) }
    }
}

fun MavenDependencyBase.toMavenCoordinates() = MavenCoordinates(
    groupId = coordinates.groupId,
    artifactId = coordinates.artifactId,
    version = coordinates.version,
    classifier = coordinates.classifier,
)

fun MavenDependencyNode.mavenCoordinates(suffix: String? = null): MavenCoordinates {
    return this.dependency
        .coordinates
        .let { if (suffix == null) it else it.copy(artifactId = "${it.artifactId}:${suffix}") }
}

fun getAmperFileCacheBuilder(userCacheRoot: AmperUserCacheRoot): FileCacheBuilder.() -> Unit =
    getDefaultFileCacheBuilder(userCacheRoot.path)

/**
 * Creates empty DR Context.
 * It might be used for initializing supplementary node holders in a resolution graph only.
 */
fun emptyContext(userCacheRoot: AmperUserCacheRoot, spanBuilder: SpanBuilderSource?): Context =
    emptyContext(getAmperFileCacheBuilder(userCacheRoot), spanBuilder)

/**
 * Creates empty DR Context.
 * It might be used for initializing supplementary node holders in a resolution graph only.
 */
fun emptyContext(fileCacheBuilder: FileCacheBuilder.() -> Unit, spanBuilder: SpanBuilderSource?): Context = Context {
    cache = fileCacheBuilder
    spanBuilder?.let { this.spanBuilder = it }
}

internal fun String.md5(): String = MessageDigest.getInstance("MD5")
    .digest(this.toByteArray())
    .decodeToString()