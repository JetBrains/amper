/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.model.Resource
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.PlexusContainer
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class MockedMavenProject : MavenProject()

/**
 * Type safe convention for [PlexusContainer.addComponent] fun.
 */
inline fun <reified Role> PlexusContainer.addDefaultComponent(component: Role) =
    addComponent(component, Role::class.java, "default")

fun DefaultArtifact(groupId: String, artifactId: String, version: String, scope: String, type: String) =
    DefaultArtifact(groupId, artifactId, version, scope, type, null, DefaultArtifactHandler())

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