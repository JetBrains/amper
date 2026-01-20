/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.download

import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.MavenRepository.Companion.MavenCentral
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeWithContext
import org.jetbrains.amper.frontend.dr.resolver.MavenResolver
import org.jetbrains.amper.frontend.dr.resolver.ResolutionDepth
import java.nio.file.Path

/**
 * Downloads a single Maven artifact and returns the path to its JAR file.
 *
 * @param coordinates The Maven coordinates of the artifact to download
 * @param repositories The repositories to search (defaults to Maven Central)
 * @return Path to the downloaded JAR file, or null if no JAR was found
 */
suspend fun MavenResolver.downloadSingleArtifactJar(
    coordinates: MavenCoordinates,
    repositories: List<Repository> = listOf(MavenCentral),
): Path? {
    val resolvedRoot = resolveWithContext(
        repositories = repositories,
        scope = ResolutionScope.RUNTIME,
        platform = ResolutionPlatform.JVM,
        resolveSourceMoniker = coordinates.toString(),
        resolutionDepth = ResolutionDepth.GRAPH_FULL,
    ) { context ->
        val pluginNode = MavenDependencyNodeWithContext(context, coordinates = coordinates, isBom = false)
        RootDependencyNodeWithContext(templateContext = context, children = listOf(pluginNode))
    }

    val dependencyNode = resolvedRoot.root.children.first() as MavenDependencyNode
    return dependencyNode.dependency.files()
        .filter { it.extension == "jar" }
        .mapNotNull { it.path }
        .singleOrNull()
}
