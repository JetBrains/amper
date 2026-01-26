/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanBuilder
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.getDefaultFileCacheBuilder
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.problems.reporting.MessageBundle
import kotlin.io.path.absolutePathString

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

fun MavenDependencyBase.toDrMavenCoordinates() = coordinates.toDrMavenCoordinates()

fun org.jetbrains.amper.frontend.MavenCoordinates.toDrMavenCoordinates() = MavenCoordinates(
    groupId = groupId,
    artifactId = artifactId,
    version = version,
    classifier = classifier,
    packagingType = packagingType,
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
fun emptyContext(userCacheRoot: AmperUserCacheRoot, openTelemetry: OpenTelemetry?, incrementalCache: IncrementalCache?): Context =
    emptyContext(getAmperFileCacheBuilder(userCacheRoot),  openTelemetry, incrementalCache)

/**
 * Creates empty DR Context.
 * It might be used for initializing supplementary node holders in a resolution graph only.
 */
fun emptyContext(fileCacheBuilder: FileCacheBuilder.() -> Unit, openTelemetry: OpenTelemetry?, incrementalCache: IncrementalCache?): Context =
    Context {
        cache = fileCacheBuilder
        openTelemetry?.let { this.openTelemetry = it }
        incrementalCache?.let { this.incrementalCache = it }
    }

fun AmperModule.uniqueModuleKey(): String = source.moduleDir.absolutePathString()

internal fun OpenTelemetry?.spanBuilder(spanName: String): SpanBuilder = (this ?: OpenTelemetry.noop())
    .getTracer("org.jetbrains.amper.dr")
    .spanBuilder(spanName)