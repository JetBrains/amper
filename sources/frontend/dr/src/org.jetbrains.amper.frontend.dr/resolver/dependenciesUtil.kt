/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.MessageBundle
import org.jetbrains.amper.dependency.resolution.AmperDependencyResolutionException
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.getDefaultFileCacheBuilder
import org.jetbrains.amper.frontend.MavenDependency
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

object FrontendDrBundle : MessageBundle("messages.FrontendDr")

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
        result.add(kClass.cast(this))
    } else {
        parents.forEach { it.findParentsImpl(kClass, visited = visited, result = result) }
    }
}

internal fun parseCoordinates(coordinates: String): MavenCoordinates {
    val parts = coordinates.trim().split(":")
    if (parts.size < 3) {
        coordinatesError(coordinates) {
            AmperDependencyResolutionException(
                FrontendDrBundle.message("dependency.coordinates.have.too.few.parts", coordinates))
        }
    }
    parts.forEach {
        // it throws InvalidPathException in case coordinates contain some restricted symbols
        try {
            Path(it)
        } catch (e: InvalidPathException) {
            coordinatesError(coordinates) { e }
        }

        if (it.contains("\n") || it.contains("\r")) {
            throw AmperDependencyResolutionException(
                FrontendDrBundle.message("dependency.coordinates.contains.multiline.parts", coordinates))
        }
        if (it.trim().contains(" ")) {
            throw AmperDependencyResolutionException(
                FrontendDrBundle.message("dependency.coordinates.contains.parts.with.spaces", coordinates))
        }
        if (it.trim().endsWith(".")) {
            // Coordinates are used for building paths to the artifacts in the Amper local storage,
            // Windows strips the trailing dots while creating directories,
            // this way path to artifacts of dependencies with coordinates 'A:B:v1' and 'A...:B.:v1..' are not distinguishable.
            // See https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file?redirectedfrom=MSDN
            throw AmperDependencyResolutionException(
                FrontendDrBundle.message("dependency.coordinates.contains.parts.ending.with.dots", coordinates))
        }
        if (it.contains("/") || it.contains("\\")) {
            // Coordinates are used for building paths to the artifacts in the Amper local storage, slashes will affect the path
            throw AmperDependencyResolutionException(
                FrontendDrBundle.message("dependency.coordinates.contains.parts.ending.with.slashes", coordinates))
        }
    }

    val groupId = parts[0].trim()
    val artifactId = parts[1].trim()
    val version = parts[2].trim()
    val classifier = if (parts.size > 3) parts[3].trim() else null

    return MavenCoordinates(groupId = groupId, artifactId = artifactId, version = version, classifier = classifier)
}

private fun coordinatesError(coordinates: String, exception: () -> Exception) {
    if (GradleScope.parseGradleScope(coordinates) != null) {
        throw AmperDependencyResolutionException(
            FrontendDrBundle.message("dependency.coordinates.in.gradle.format", coordinates))
    } else {
        throw exception()
    }
}

@UsedInIdePlugin
enum class GradleScope {
    api,
    implementation, compile,
    testImplementation, testCompile,
    compileOnly,
    compileOnlyApi,
    testCompileOnly,
    runtimeOnly, runtime,
    testRuntimeOnly, testRuntime;

    companion object {
        fun parseGradleScope(coordinates: String): Pair<GradleScope, String>? =
            GradleScope.entries
                .firstOrNull { coordinates.startsWith("${it.name}(") }
                ?.let { gradleScope ->
                    val gradleScopePrefix = "${gradleScope.name}("
                    val trimmedCoordinates = trimPrefixAndSuffixOrNull(coordinates, "$gradleScopePrefix\"", "\")")
                        ?: trimPrefixAndSuffixOrNull(coordinates, "$gradleScopePrefix'", "')")
                        ?: return@let null
                    gradleScope to trimmedCoordinates
                }

        private fun trimPrefixAndSuffixOrNull(coordinates: String, prefix: String, suffix: String): String? =
            coordinates
                .takeIf { it.startsWith(prefix) && it.endsWith(suffix) }
                ?.substringAfter(prefix)
                ?.substringBefore(suffix)
    }
}

internal fun MavenDependency.parseCoordinates(): MavenCoordinates {
    return parseCoordinates(this.coordinates.value)
}

fun MavenDependencyNode.mavenCoordinates(suffix: String? = null): MavenCoordinates {
    return this.dependency
        .coordinates
        .let { if (suffix == null) it else it.copy(artifactId = "${it.artifactId}:${suffix}") }
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
