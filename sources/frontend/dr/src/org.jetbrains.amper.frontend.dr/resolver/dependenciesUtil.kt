/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.getDefaultFileCacheBuilder
import org.jetbrains.amper.dependency.resolution.resolveSafeOrNull
import org.jetbrains.amper.frontend.MavenDependency
import org.slf4j.LoggerFactory
import kotlin.io.path.Path

internal val logger = LoggerFactory.getLogger("files.kt")

@Suppress("UNUSED") // Used in Idea plugin
val DependencyNode.fragmentDependencies: List<DirectFragmentDependencyNodeHolder>
    get() = findParents<DirectFragmentDependencyNodeHolder>()

private inline fun <reified T: DependencyNode> DependencyNode.findParents(): List<T> {
    val result = mutableSetOf<T>()
    findParentsImpl(T::class.java, result = result)
    return result.toList()
}

private fun <T: DependencyNode> DependencyNode.findParentsImpl(
    kClass: Class<T>,
    visited: MutableSet<DependencyNode> = mutableSetOf(),
    result: MutableSet<T> = mutableSetOf()
) {
    if (!visited.add(this)) {
        return
    }

    if (kClass.isInstance(this)) {
        @Suppress("UNCHECKED_CAST")
        result.add(this as T)
    } else {
        parents.forEach { it.findParentsImpl(kClass, visited = visited, result = result) }
    }
}

internal fun parseCoordinates(coordinates: String): MavenCoordinates? {
    val parts = coordinates.split(":")
    if (parts.size < 3) {
        return null
    }
    if (parts.any { resolveSafeOrNull{ Path(it) } == null } ) {
        // Check if resolved parts don't contain illegal characters
        return null
    }
    return MavenCoordinates(parts[0], parts[1], parts[2], classifier = if (parts.size > 3) parts[3] else null)
}

internal fun MavenDependency.parseCoordinates(): MavenCoordinates? {
    return parseCoordinates(this.coordinates)
}

fun MavenDependencyNode.mavenCoordinates(suffix: String? = null): MavenCoordinates {
    return MavenCoordinates(
        groupId = this.dependency.group,
        artifactId = if (suffix == null) dependency.module else "${dependency.module}:${suffix}",
        version = this.dependency.version,
    )
}

/**
 * Describes coordinates of a Maven artifact.
 */
data class MavenCoordinates(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String? = null
) {
    override fun toString(): String {
        return "$groupId:$artifactId:$version${if (classifier != null) ":$classifier" else ""}"
    }
}

fun getDefaultAmperFileCacheBuilder(): FileCacheBuilder.() -> Unit = getAmperFileCacheBuilder(AmperUserCacheRoot.fromCurrentUser())

fun getAmperFileCacheBuilder(userCacheRoot: AmperUserCacheRoot): FileCacheBuilder.() -> Unit = getDefaultFileCacheBuilder(userCacheRoot.path)

/**
 * Creates empty DR Context.
 * It might be used for initializing supplementary node holders in a resolution graph only.
 */
fun emptyContext(userCacheRoot: AmperUserCacheRoot): Context = emptyContext(getAmperFileCacheBuilder(userCacheRoot))

/**
 * Creates empty DR Context.
 * It might be used for initializing supplementary node holders in a resolution graph only.
 */
fun emptyContext(fileCacheBuilder: FileCacheBuilder.() -> Unit): Context = Context {
    cache = fileCacheBuilder
}