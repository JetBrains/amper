/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.bridge.MavenRepositorySystem
import org.apache.maven.classrealm.ClassRealmManager
import org.apache.maven.doxia.siterenderer.SiteRenderer
import org.apache.maven.doxia.tools.SiteTool
import org.apache.maven.execution.MavenExecutionRequest
import org.apache.maven.lifecycle.internal.LifecycleExecutionPlanCalculator
import org.apache.maven.model.Resource
import org.apache.maven.plugin.BuildPluginManager
import org.apache.maven.plugin.MavenPluginManager
import org.apache.maven.project.MavenProject
import org.apache.maven.session.scope.internal.SessionScope
import org.codehaus.plexus.PlexusContainer
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.tasks.rootFragment
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class MockedMavenProject(other: MavenProject) : MavenProject(other) {
    constructor() : this(MavenProject())

    private val _newSourceRoots = mutableListOf<String>()
    val newSourceRoots: List<String> get() = _newSourceRoots

    private val _newTestSourceRoots = mutableListOf<String>()
    val newTestSourceRoots: List<String> get() = _newTestSourceRoots

    override fun addCompileSourceRoot(path: String?) {
        super.addCompileSourceRoot(path)
        path?.let { _newSourceRoots.add(it) }
    }

    override fun addTestCompileSourceRoot(path: String?) {
        super.addTestCompileSourceRoot(path)
        path?.let { _newTestSourceRoots.add(it) }
    }
}

// Various maven services.
internal val PlexusContainer.mavenPluginManager: MavenPluginManager get() = lookup(MavenPluginManager::class.java)
internal val PlexusContainer.buildPluginManager get() = lookup(BuildPluginManager::class.java)
internal val PlexusContainer.siteTool get() = lookup(SiteTool::class.java)
internal val PlexusContainer.siteRenderer get() = lookup(SiteRenderer::class.java)
internal val PlexusContainer.sessionScope get() = lookup(SessionScope::class.java)
internal val PlexusContainer.repoSystem get() = lookup(MavenRepositorySystem::class.java)
internal val PlexusContainer.lifecycleExecutionPlanCalculator get() = lookup(LifecycleExecutionPlanCalculator::class.java)
internal val PlexusContainer.classRealmManager get() = lookup(ClassRealmManager::class.java)

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
inline fun MavenExecutionRequest.addRemoteRepository(repository: () -> MavenArtifactRepository): MavenExecutionRequest =
    addRemoteRepository(repository())

inline fun MavenExecutionRequest.addServer(configure: MavenServer.() -> Unit): MavenExecutionRequest =
    addServer(MavenServer().apply(configure))