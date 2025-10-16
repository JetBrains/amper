/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.model.Resource
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.PlexusContainer
import org.eclipse.aether.graph.DefaultDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.group
import org.jetbrains.amper.dependency.resolution.module
import org.jetbrains.amper.dependency.resolution.version
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.tasks.rootFragment
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class MockedMavenProject : MavenProject()

// --- Plexus container utilities ---
/**
 * Type safe convention for [PlexusContainer.addComponent] fun.
 */
inline fun <reified Role> PlexusContainer.addDefaultComponent(component: Role) =
    addComponent(component, Role::class.java, "default")

// --- Maven project model changing utilities ---
fun MavenProject.addCompileSourceRoots(paths: List<Path>) =
    paths.forEach { addCompileSourceRoot(it.absolutePathString()) }

fun MavenProject.addTestCompileSourceRoots(paths: List<Path>) =
    paths.forEach { addTestCompileSourceRoot(it.absolutePathString()) }

fun MavenProject.addResources(paths: List<Path>) = paths
    .map { Resource().apply { directory = it.absolutePathString() } }
    .forEach { addResource(it) }

fun MavenProject.addTestResources(paths: List<Path>) = paths
    .map { Resource().apply { directory = it.absolutePathString() } }
    .forEach { addTestResource(it) }

// --- Maven artifacts utilities and typealiases ---
typealias MavenArtifact = org.apache.maven.artifact.Artifact
typealias DefaultMavenArtifact = org.apache.maven.artifact.DefaultArtifact

/**
 * Shortcut for the default constructor with `isAddedToClasspath` flag being provided.
 */
fun DefaultMavenArtifact(
    groupId: String,
    artifactId: String,
    version: String,
    scope: String,
    type: String,
    isAddedToClasspath: Boolean = true,
): MavenArtifact = DefaultMavenArtifact(
    /* groupId = */ groupId,
    /* artifactId = */ artifactId,
    /* version = */ version,
    /* scope = */ scope,
    /* type = */ type,
    /* classifier = */ null,
    // TODO Need to be think again about this flag.
    /* artifactHandler = */ DefaultArtifactHandler().apply { this.isAddedToClasspath = isAddedToClasspath },
)

fun AmperModule.asMavenArtifact(scope: String) = DefaultMavenArtifact(
    groupId = rootFragment.settings.publishing.group ?: "unspecified",
    artifactId = rootFragment.settings.publishing.name ?: userReadableName,
    version = rootFragment.settings.publishing.version ?: "unspecified",
    scope = scope,
    type = "jar",
)

// --- Aether artifacts utilities and typealiases ---
typealias AetherDependencyNode = DefaultDependencyNode
typealias DefaultAetherArtifact = org.eclipse.aether.artifact.DefaultArtifact
typealias AetherArtifact = org.eclipse.aether.artifact.Artifact
typealias AetherDependency = org.eclipse.aether.graph.Dependency

suspend fun MavenDependencyNode.toAetherDependencyRecursive(extension: String?): AetherDependencyNode =
    toAetherDependency(extension).apply {
        children = this@toAetherDependencyRecursive.children
            .filterIsInstance<MavenDependencyNode>()
            .map { it.toAetherDependencyRecursive(extension) }
    }

suspend fun MavenDependencyNode.toAetherDependency(extension: String?): AetherDependencyNode =
    AetherDependencyNode(AetherDependency(toAetherArtifact(extension), "runtime"))

suspend fun MavenDependencyNode.toAetherArtifact(extension: String?): AetherArtifact =
    DefaultAetherArtifact(
        /* groupId = */ group,
        /* artifactId = */ module,
        /* classifier = */ "runtime",
        /* extension = */ extension,
        /* version = */ dependency.version,
        /* type = */ null,
    ).run {
        val file = when {
            isBom || extension == "pom" -> 
                dependency.pomPath!!.toFile()
            extension == null || extension == "" || extension == "jar" ->
                dependency.files().first().path!!.toFile()
            // TODO Think about that?
            else -> error("Unsupported extension: $extension")
        }
        setFile(file)
    }