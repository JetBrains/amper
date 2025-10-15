/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.collection.CollectResult
import org.eclipse.aether.graph.DefaultDependencyNode
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.impl.ArtifactResolver
import org.eclipse.aether.impl.DependencyCollector
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResult
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenRepository.Companion.MavenCentral
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeHolder
import org.jetbrains.amper.resolver.MavenResolver

/**
 * A bridge between Amper DR and corresponding Maven components.
 */
class AmperMavenDRBridge(private val mavenResolver: MavenResolver) : DependencyCollector, ArtifactResolver {

    override fun resolveArtifact(session: RepositorySystemSession, request: ArtifactRequest): ArtifactResult {
        val resultRoot = runBlocking(Dispatchers.IO) {
            resolveArtifacts(listOf(request.artifact), request.artifact.extension)
        }.single()
        return ArtifactResult(request).apply { artifact = resultRoot.artifact }
    }

    override fun collectDependencies(session: RepositorySystemSession, request: CollectRequest): CollectResult {
        // FIXME!!! Need to do something about that.
        val requestedExtension = request.root?.artifact?.extension ?: "jar"
        val requestedArtifacts = request.root?.artifact?.let(::listOf)
            ?: request.dependencies?.map { it.artifact }.orEmpty()

        val resolved = runBlocking(Dispatchers.IO) {
            resolveArtifacts(
                requestedArtifacts,
                requestedExtension,
            )
        }
        
        return CollectResult(request).apply {
            root = if (request.root != null) resolved.single()
            else AetherDependencyNode(request.rootArtifact).apply {
                requestContext = request.requestContext
                repositories = request.repositories
                children = resolved
            }
        }
    }

    private suspend fun resolveArtifacts(
        artifacts: List<AetherArtifact>,
        extension: String,
    ): List<AetherDependencyNode> {
        if (artifacts.isEmpty()) return emptyList()

        val resolved = mavenResolver.resolveWithContext(
            repositories = listOf(MavenCentral),
            scope = ResolutionScope.RUNTIME,
            platform = ResolutionPlatform.JVM,
            resolveSourceMoniker = "", //TODO
        ) {
            val children = artifacts.map {
                MavenDependencyNode(
                    group = it.groupId,
                    module = it.artifactId,
                    version = it.version,
                    // FIXME!!! Need to do something about that.
                    isBom = it.artifactId.endsWith("-bom")
                )
            }
            RootDependencyNodeHolder(children)
        }

        // The first element is known to be a MavenDependencyNode as we had set it.
        return resolved.children.filterIsInstance<MavenDependencyNode>()
            .map { it.toAetherDependencyRecursive(extension) }
    }

    override fun resolveArtifacts(session: RepositorySystemSession, requests: Collection<ArtifactRequest>) =
        requests.map { resolveArtifact(session, it) }
}

typealias AetherDependencyNode = DefaultDependencyNode
typealias DefaultAetherArtifact = DefaultArtifact
typealias AetherArtifact = Artifact
typealias AetherDependency = Dependency

suspend fun MavenDependencyNode.toAetherDependencyRecursive(extension: String?): AetherDependencyNode =
    toAetherDependency(extension).apply {
        children = this@toAetherDependencyRecursive.children
            .filterIsInstance<MavenDependencyNode>()
            .map { it.toAetherDependencyRecursive(extension) }
    }

suspend fun MavenDependencyNode.toAetherDependency(extension: String?): AetherDependencyNode =
    AetherDependencyNode(AetherDependency(toAetherArtifact(extension), "runtime"))

suspend fun MavenDependencyNode.toAetherArtifact(extension: String?): Artifact =
    DefaultAetherArtifact(
        /* groupId = */ group,
        /* artifactId = */ module,
        /* classifier = */ "runtime",
        /* extension = */ extension,
        /* version = */ version,
        /* type = */ null,
    ).run {
        setFile(
            when (extension) {
                null, "", "jar" -> dependency.files().first().getPath()!!.toFile()
                "pom" -> dependency.pom.getPath()!!.toFile()
                // TODO Think about that?
                else -> error("Unsupported extension: $extension")
            }
        )
    }