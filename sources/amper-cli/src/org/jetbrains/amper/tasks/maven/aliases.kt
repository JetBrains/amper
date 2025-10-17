/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.apache.maven.artifact.Artifact
import org.apache.maven.artifact.DefaultArtifact
import org.eclipse.aether.graph.DefaultDependencyNode
import org.eclipse.aether.graph.Dependency

// Maven model aliases.
typealias MavenArtifact = Artifact
typealias DefaultMavenArtifact = DefaultArtifact
typealias MavenArtifactRepository = org.apache.maven.artifact.repository.ArtifactRepository
typealias MavenServer = org.apache.maven.settings.Server
typealias MavenDependencyManagement = org.apache.maven.model.DependencyManagement

// Aether model aliases.
typealias AetherDependencyNode = DefaultDependencyNode
typealias DefaultAetherArtifact = org.eclipse.aether.artifact.DefaultArtifact
typealias AetherArtifact = org.eclipse.aether.artifact.Artifact
typealias AetherDependency = Dependency