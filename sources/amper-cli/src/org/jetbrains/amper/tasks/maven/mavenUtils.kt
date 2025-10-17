/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.execution.MavenExecutionRequest
import org.apache.maven.model.Resource
import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.tasks.rootFragment
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class MockedMavenProject(other: MavenProject) : MavenProject(other) {
    constructor() : this(MavenProject())

    private val _newSourceRoots = mutableListOf<String>()
    val newSourceRoots: List<String> by ::_newSourceRoots

    private val _newTestSourceRoots = mutableListOf<String>()
    val newTestSourceRoots: List<String> by ::_newTestSourceRoots

    override fun addCompileSourceRoot(path: String?) {
        super.addCompileSourceRoot(path)
        path?.let { _newSourceRoots.add(it) }
    }

    override fun addTestCompileSourceRoot(path: String?) {
        super.addTestCompileSourceRoot(path)
        path?.let { _newTestSourceRoots.add(it) }
    }
}

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

// --- Maven artifacts utilities ---
/**
 * Shortcut for the default constructor with `isAddedToClasspath` flag being provided.
 */
fun DefaultMavenArtifact(
    groupId: String,
    artifactId: String,
    version: String,
    scope: String,
    type: String,
    extension: String = type,
    isAddedToClasspath: Boolean = true,
): MavenArtifact = DefaultMavenArtifact(
    /* groupId = */ groupId,
    /* artifactId = */ artifactId,
    /* version = */ version,
    /* scope = */ scope,
    /* type = */ type,
    /* classifier = */ null,
    /* artifactHandler = */ DefaultArtifactHandler(type).apply {
        // TODO Need to be think again about this flag.
        this.isAddedToClasspath = isAddedToClasspath
        this.isIncludesDependencies = false
        this.extension = extension
    },
)

fun AmperModule.asMavenArtifact(scope: String) = DefaultMavenArtifact(
    groupId = rootFragment.settings.publishing.group ?: "unspecified",
    artifactId = rootFragment.settings.publishing.name ?: userReadableName,
    version = rootFragment.settings.publishing.version ?: "unspecified",
    scope = scope,
    type = "jar",
)

// Kotlin-styled shortcuts.
@Suppress("TYPEALIAS_EXPANSION_DEPRECATION")
inline fun MavenExecutionRequest.addRemoteRepository(repository: () -> MavenArtifactRepository): MavenExecutionRequest =
    addRemoteRepository(repository())

inline fun MavenExecutionRequest.addServer(configure: MavenServer.() -> Unit): MavenExecutionRequest =
    addServer(MavenServer().apply(configure))