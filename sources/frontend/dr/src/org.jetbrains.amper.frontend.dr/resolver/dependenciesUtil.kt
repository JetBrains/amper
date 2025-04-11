/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.MessageBundle
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.SpanBuilderSource
import org.jetbrains.amper.dependency.resolution.getDefaultFileCacheBuilder
import org.jetbrains.amper.frontend.BomDependency
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.MavenDependencyBase
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

object FrontendDrBundle : MessageBundle("messages.FrontendDrBundle")

val DependencyNode.fragmentDependencies: List<DirectFragmentDependencyNodeHolder>
    get() = findParents<DirectFragmentDependencyNodeHolder>()

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

sealed interface ParsedCoordinates {
    class Success(val coordinates: MavenCoordinates) : ParsedCoordinates
    class Failure(val errors: List<String>): ParsedCoordinates {
        constructor(error: String) : this(listOf(error))
    }
}

fun MavenDependencyBase.parseCoordinates(): ParsedCoordinates {
    val mavenCoordinates = when(this) {
        is MavenDependency -> coordinates.value.trim()
        // todo (AB) : Remove this after BomDependency coordinates started to be parsed without prefix "bom:"
        is BomDependency -> coordinates.value.trim().removePrefix("bom:")
    }

    return parseCoordinates(mavenCoordinates)
}

private fun parseCoordinates(coordinates: String): ParsedCoordinates {
    val parts = coordinates.trim().split(":")

    if (parts.size < 2) {
        val errors = buildList {
            add(FrontendDrBundle.message("dependency.coordinates.have.too.few.parts", coordinates))
            reportIfCoordinatesAreGradleLike(coordinates, this)
        }
        return ParsedCoordinates.Failure(errors)
    }
    parts.forEach { part ->
        // It throws InvalidPathException in case coordinates contain some restricted symbols.
        // We use coordinates parts as folders to store the artifacts; thus, all the paths should be valid folder names.
        try {
            Path(part)
        } catch (e: InvalidPathException) {
            val errors = buildList {
                add(FrontendDrBundle.message("dependency.coordinates.contains.parts.with.forbidden.characters", coordinates, part, e.message))
                reportIfCoordinatesAreGradleLike(coordinates, this)
            }
            return ParsedCoordinates.Failure(errors)
        }

        if (part.contains("\n") || part.contains("\r")) {
            return ParsedCoordinates.Failure(FrontendDrBundle.message("dependency.coordinates.contains.multiline.parts", coordinates))
        }
        if (part.trim().contains(" ")) {
            return ParsedCoordinates.Failure(FrontendDrBundle.message("dependency.coordinates.contains.parts.with.spaces", coordinates))
        }
        if (part.trim().endsWith(".")) {
            // Coordinates are used for building paths to the artifacts in the Amper local storage,
            // Windows strips the trailing dots while creating directories,
            // this way path to artifacts of dependencies with coordinates 'A:B:v1' and 'A...:B.:v1..' are not distinguishable.
            // See https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file?redirectedfrom=MSDN
            return ParsedCoordinates.Failure(FrontendDrBundle.message("dependency.coordinates.contains.parts.ending.with.dots", coordinates))
        }
        if (part.contains("/") || part.contains("\\")) {
            // Coordinates are used for building paths to the artifacts in the Amper local storage, slashes will affect the path
            return ParsedCoordinates.Failure(FrontendDrBundle.message("dependency.coordinates.contains.parts.ending.with.slashes", coordinates))
        }
    }
    if (parts.size > 4) {
        return ParsedCoordinates.Failure(FrontendDrBundle.message("dependency.coordinates.have.too.many.parts", coordinates))
    }

    val groupId = parts[0].trim()
    val artifactId = parts[1].trim()
    val version = if (parts.size > 2) parts[2].trim() else null
    val classifier = if (parts.size > 3) parts[3].trim() else null

    return ParsedCoordinates.Success(
        MavenCoordinates(groupId = groupId, artifactId = artifactId, version = version, classifier = classifier))
}

private fun reportIfCoordinatesAreGradleLike(coordinates: String, messages: MutableList<String>) {
    if (GradleScope.parseGradleScope(coordinates) != null) {
        messages += FrontendDrBundle.message("dependency.coordinates.in.gradle.format", coordinates)
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
fun emptyContext(fileCacheBuilder: FileCacheBuilder.() -> Unit,spanBuilder: SpanBuilderSource?): Context = Context {
    cache = fileCacheBuilder
    spanBuilder?.let { this.spanBuilder = it }
}
