/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.dependency.resolution.metadata.json.AvailableAt
import org.jetbrains.amper.dependency.resolution.metadata.json.Capability
import org.jetbrains.amper.dependency.resolution.metadata.json.Dependency
import org.jetbrains.amper.dependency.resolution.metadata.json.Variant
import org.jetbrains.amper.dependency.resolution.metadata.json.Version
import org.jetbrains.amper.dependency.resolution.metadata.json.parseMetadata
import org.jetbrains.amper.dependency.resolution.metadata.xml.Dependencies
import org.jetbrains.amper.dependency.resolution.metadata.xml.DependencyManagement
import org.jetbrains.amper.dependency.resolution.metadata.xml.Project
import org.jetbrains.amper.dependency.resolution.metadata.xml.expandTemplates
import org.jetbrains.amper.dependency.resolution.metadata.xml.parsePom
import org.jetbrains.amper.dependency.resolution.metadata.xml.plus

class MavenDependencyNode(
    val resolver: Resolver,
    val group: String,
    val module: String,
    val version: String,
) : DependencyNode {

    constructor(resolver: Resolver, dependency: MavenDependency) : this(
        resolver,
        dependency.group,
        dependency.module,
        dependency.version,
    )

    val dependency: MavenDependency
        get() = createOrReuseDependency(resolver, group, module, version)

    override var state: ResolutionState by dependency::state
    override var level: ResolutionLevel by dependency::level
    override val children: Collection<DependencyNode>
        get() = dependency.children.map { MavenDependencyNode(resolver, it) }
    override val messages: Collection<Message> by dependency::messages

    override fun resolve(level: ResolutionLevel) {
        dependency.resolve(resolver, level)
    }

    override fun downloadDependencies() {
        dependency.downloadDependencies(resolver)
    }

    override fun toString(): String = if (dependency.version == version) {
        dependency.toString()
    } else {
        "$group:$module:$version -> ${dependency.version}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass || other !is MavenDependencyNode) return false
        return dependency == other.dependency
    }

    override fun hashCode(): Int = dependency.hashCode()
}

private fun createOrReuseDependency(
    resolver: Resolver,
    group: String,
    module: String,
    version: String
): MavenDependency {
    val dep = resolver.cache.computeIfAbsent(getKey(group, module)) {
        MavenDependency(
            resolver.settings.fileCache,
            group,
            module,
            version
        )
    }
    if (version == dep.version) {
        return dep
    }
    val prev = ComparableVersion(dep.version)
    val curr = ComparableVersion(version)
    if (curr <= prev) {
        return dep
    }
    return MavenDependency(resolver.settings.fileCache, group, module, version).also {
        resolver.cache[getKey(group, module)] = it
    }
}

private fun getKey(group: String, module: String) = Key<MavenDependency>("$group:$module")

data class MavenDependency(
    val fileCache: List<CacheDirectory>,
    val group: String,
    val module: String,
    val version: String
) {

    var state: ResolutionState = ResolutionState.UNKNOWN
    var level: ResolutionLevel = ResolutionLevel.CREATED

    val children = mutableListOf<MavenDependency>()
    var variant: Variant? = null
    var packaging: String? = null
    val messages: MutableCollection<Message> = mutableListOf()

    val metadata = DependencyFile(fileCache, this, "module")
    val pom = DependencyFile(fileCache, this, "pom")
    val files
        get() = buildMap {
            variant?.files?.forEach {
                val extension = it.name.substringAfterLast('.')
                put(extension, DependencyFile(fileCache, this@MavenDependency, extension))
            }
            packaging?.let {
                val extension = when (it) {
                    "bundle" -> "jar"
                    else -> it
                }
                put(extension, DependencyFile(fileCache, this@MavenDependency, extension))
            }
            if (isEmpty()) {
                put("jar", DependencyFile(fileCache, this@MavenDependency, "jar"))
            }
        }

    override fun toString(): String = "$group:$module:$version"

    fun resolve(resolver: Resolver, level: ResolutionLevel) {
        if (metadata.isDownloadedOrDownload(level, resolver)) {
            resolveUsingMetadata(resolver, level)
        } else if (pom.isDownloadedOrDownload(level, resolver)) {
            resolveUsingPom(resolver, level)
        } else {
            messages += Message(
                "Either metadata or pom required for $this",
                resolver.settings.repositories.toString(),
                if (level == ResolutionLevel.FULL) Severity.ERROR else Severity.WARNING,
            )
        }
    }

    private fun resolveUsingMetadata(resolver: Resolver, level: ResolutionLevel) {
        val text = metadata.readText()
        val module = try {
            text.parseMetadata()
        } catch (e: Exception) {
            messages += Message(
                "Unable to parse metadata file $metadata",
                e.toString(),
                Severity.ERROR,
            )
            return
        }
        module.variants.filter {
            it.capabilities.isEmpty() || it.capabilities.singleOrNull() == toCapability()
                    || isKotlinTestJunit() && it.capabilities.sortedBy { it.name } == listOf(
                Capability(group, "kotlin-test-framework-impl", version),
                toCapability()
            ) || isGuava() && it.capabilities.sortedBy { it.name } == listOf(
                Capability("com.google.collections", "google-collections", version),
                toCapability()
            )
        }.filter {
            val kotlinPlatformType = it.attributes["org.jetbrains.kotlin.platform.type"]
            (kotlinPlatformType == null || kotlinPlatformType == resolver.settings.platform)
                    && when (resolver.settings.scope) {
                Scope.COMPILE -> it.attributes["org.gradle.usage"]?.endsWith("-api") == true
                Scope.RUNTIME -> it.attributes["org.gradle.usage"]?.endsWith("-runtime") == true
            }
        }.filter {
            !isGuava() || it.attributes["org.gradle.jvm.environment"]?.endsWith(resolver.settings.platform) == true
        }.also {
            if (it.size <= 1) {
                variant = it.singleOrNull()
            } else {
                messages += Message(
                    "More than a single variant provided",
                    it.joinToString { it.name },
                    Severity.WARNING,
                )
            }
        }.flatMap {
            it.dependencies + listOfNotNull(it.`available-at`?.asDependency())
        }.map {
            createOrReuseDependency(resolver, it.group, it.module, it.version.requires)
        }.let {
            children.addAll(it)
            state = level.state
        }
    }

    private fun isKotlinTestJunit(): Boolean =
        group == "org.jetbrains.kotlin" && (module in setOf("kotlin-test-junit", "kotlin-test-junit5"))

    private fun isGuava(): Boolean = group == "com.google.guava" && module == "guava"

    private fun MavenDependency.toCapability(): Capability = Capability(group, module, version)

    private fun AvailableAt.asDependency(): Dependency = Dependency(group, module, Version(version))

    private fun resolveUsingPom(resolver: Resolver, resolutionLevel: ResolutionLevel) {
        val text = pom.readText()
        if (!text.contains("do_not_remove: published-with-gradle-metadata")) {
            val project = try {
                text.parsePom().resolve(resolver, resolutionLevel)
            } catch (e: Exception) {
                messages += Message(
                    "Unable to parse pom file $pom",
                    e.toString(),
                    Severity.ERROR,
                )
                return
            }
            packaging = project.packaging
            (project.dependencies?.dependencies ?: listOf()).filter {
                when (resolver.settings.scope) {
                    Scope.COMPILE -> it.scope == null || it.scope == "compile"
                    Scope.RUNTIME -> it.scope == null || it.scope == "compile" || it.scope == "runtime"
                }
            }.filter {
                it.version != null && it.optional != true
            }.map {
                createOrReuseDependency(resolver, it.groupId, it.artifactId, it.version!!)
            }.let {
                children.addAll(it)
                state = resolutionLevel.state
            }
        } else {
            messages += Message(
                "Pom provided but metadata required for $this",
                resolver.settings.repositories.toString(),
                if (resolutionLevel.state == ResolutionState.RESOLVED) Severity.ERROR else Severity.WARNING,
            )
        }
    }

    private fun Project.resolve(
        resolver: Resolver,
        resolutionLevel: ResolutionLevel,
        depth: Int = 0,
        origin: Project = this
    ): Project {
        if (depth > 10) {
            messages += Message(
                "Project ${origin.name} has more than ten ancestors",
                severity = Severity.WARNING,
            )
            return this
        }
        val parentNode = parent?.let {
            MavenDependency(resolver.settings.fileCache, it.groupId, it.artifactId, it.version)
        }
        val project = if (parentNode != null && (parentNode.pom.isDownloadedOrDownload(resolutionLevel, resolver))) {
            val text = parentNode.pom.readText()
            val parentProject = text.parsePom().resolve(resolver, resolutionLevel, depth + 1, origin)
            copy(
                groupId = groupId ?: parentProject.groupId,
                artifactId = artifactId ?: parentProject.artifactId,
                version = version ?: parentProject.version,
                dependencies = dependencies + parentProject.dependencies,
                dependencyManagement = dependencyManagement + parentProject.dependencyManagement,
                properties = properties + parentProject.properties,
            )
        } else if (parent != null && (groupId == null || artifactId == null || version == null)) {
            copy(
                groupId = groupId ?: parent.groupId,
                artifactId = artifactId ?: parent.artifactId,
                version = version ?: parent.version,
            )
        } else {
            this
        }
        val dependencyManagement = project.dependencyManagement
            ?.dependencies
            ?.dependencies
            ?.map { it.expandTemplates(project) }
            ?.flatMap {
                if (it.scope == "import" && it.version != null) {
                    val dependency = MavenDependency(resolver.settings.fileCache, it.groupId, it.artifactId, it.version)
                    if (dependency.pom.isDownloadedOrDownload(resolutionLevel, resolver)) {
                        val text = dependency.pom.readText()
                        val dependencyProject = text.parsePom().resolve(resolver, resolutionLevel, depth + 1, origin)
                        dependencyProject.dependencyManagement?.dependencies?.dependencies ?: listOf(it)
                    } else {
                        listOf(it)
                    }
                } else {
                    listOf(it)
                }
            }
        val dependencies = project.dependencies
            ?.dependencies
            ?.map {
                if (it.version == null) {
                    val dependency = dependencyManagement?.find { dep ->
                        dep.groupId == it.groupId && dep.artifactId == it.artifactId
                    }
                    dependency?.version?.let { version -> it.copy(version = version) } ?: it
                } else {
                    it
                }
            }
            ?.map { it.expandTemplates(project) }
        return project.copy(
            dependencies = dependencies?.let { Dependencies(it) },
            dependencyManagement = dependencyManagement?.let { DependencyManagement(Dependencies(it)) },
        )
    }

    private fun DependencyFile.isDownloadedOrDownload(level: ResolutionLevel, resolver: Resolver) =
        isDownloaded(level, resolver) || level == ResolutionLevel.FULL && download(resolver)

    fun downloadDependencies(resolver: Resolver) {
        files.values.filter { !it.isDownloaded(ResolutionLevel.FULL, resolver) }.forEach { it.download(resolver) }
    }
}
