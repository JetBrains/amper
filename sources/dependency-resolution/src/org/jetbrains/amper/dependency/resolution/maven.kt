/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

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
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class MavenDependencyNode(
    override val context: Context,
    var dependency: MavenDependency,
) : DependencyNode {

    constructor(context: Context, group: String, module: String, version: String) : this(
        context,
        createOrReuseDependency(context, group, module, version),
    )

    val group: String = dependency.group
    val module: String = dependency.module
    val version: String = dependency.version

    override val key: Key<*> = Key<MavenDependency>("$group:$module")
    override var state: ResolutionState
        get() = dependency.state
        set(value) {
            dependency.state = value
        }
    override var level: ResolutionLevel
        get() = dependency.level
        set(value) {
            dependency.level = value
        }
    override val children: List<DependencyNode> by PropertyWithDependency(
        value = listOf<MavenDependencyNode>(),
        dependency = listOf<DependencyNode>(),
        valueProvider = { thisRef ->
            thisRef.dependency.children.map { MavenDependencyNode(thisRef.context, it) }
        },
        dependencyProvider = { thisRef ->
            thisRef.dependency.children.toList()
        }
    )
    override val messages: List<Message>
        get() = dependency.messages

    override fun resolve(level: ResolutionLevel) {
        dependency.resolve(context, level)
    }

    override fun downloadDependencies() {
        dependency.downloadDependencies(context.settings)
    }

    override fun toString(): String = if (dependency.version == version) {
        dependency.toString()
    } else {
        "$group:$module:$version -> ${dependency.version}"
    }
}

class PropertyWithDependency<in T, out V, D>(
    private var value: V,
    private var dependency: D,
    val valueProvider: (T) -> V,
    val dependencyProvider: (T) -> D
) : ReadOnlyProperty<T, V> {
    override fun getValue(thisRef: T, property: KProperty<*>): V {
        val newDependency = dependencyProvider(thisRef)
        if (dependency != newDependency) {
            dependency = newDependency
            value = valueProvider(thisRef)
        }
        return value
    }
}

private fun createOrReuseDependency(
    context: Context,
    group: String,
    module: String,
    version: String
): MavenDependency = context.cache.computeIfAbsent(Key<MavenDependency>("$group:$module:$version")) {
    MavenDependency(context.settings.fileCache, group, module, version)
}

class MavenDependency internal constructor(
    val fileCache: FileCache,
    val group: String,
    val module: String,
    val version: String
) {

    var state: ResolutionState = ResolutionState.UNKNOWN
    var level: ResolutionLevel = ResolutionLevel.CREATED

    val children: MutableList<MavenDependency> = mutableListOf()
    var variant: Variant? = null
    var packaging: String? = null
    val messages: MutableList<Message> = mutableListOf()

    val metadata = getDependencyFile(this, getNameWithoutExtension(this), "module")
    val pom = getDependencyFile(this, getNameWithoutExtension(this), "pom")
    val files
        get() = buildMap {
            variant?.files?.forEach {
                val extension = it.name.substringAfterLast('.')
                put(
                    extension,
                    getDependencyFile(this@MavenDependency, getNameWithoutExtension(this@MavenDependency), extension)
                )
            }
            packaging?.let {
                val extension = when (it) {
                    "bundle" -> "jar"
                    else -> it
                }
                put(
                    extension,
                    getDependencyFile(this@MavenDependency, getNameWithoutExtension(this@MavenDependency), extension)
                )
            }
            if (isEmpty()) {
                put(
                    "jar",
                    getDependencyFile(this@MavenDependency, getNameWithoutExtension(this@MavenDependency), "jar")
                )
            }
        }

    override fun toString(): String = "$group:$module:$version"

    fun resolve(context: Context, level: ResolutionLevel) {
        val settings = context.settings
        // 1. Download pom.
        val pomText = if (pom.isDownloadedOrDownload(level, settings)) {
            pom.readText()
        } else {
            messages += Message(
                "Pom required for $this",
                settings.repositories.toString(),
                if (level == ResolutionLevel.NETWORK) Severity.ERROR else Severity.WARNING,
            )
            null
        }
        // 2. If pom is missing or mentions metadata, use it.
        if (pomText == null || pomText.contains("do_not_remove: published-with-gradle-metadata")) {
            if (metadata.isDownloadedOrDownload(level, settings)) {
                resolveUsingMetadata(context, level)
                return
            }
            if (pomText != null) {
                messages += Message(
                    "Pom provided but metadata required for $this",
                    context.settings.repositories.toString(),
                    if (level.state == ResolutionState.RESOLVED) Severity.ERROR else Severity.WARNING,
                )
            }
        }
        // 3. If can't use metadata, use pom.
        if (pomText != null) {
            resolveUsingPom(pomText, context, level)
        }
    }

    private fun resolveUsingMetadata(context: Context, level: ResolutionLevel) {
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
            (kotlinPlatformType == null || kotlinPlatformType == context.settings.platform)
                    && when (context.settings.scope) {
                Scope.COMPILE -> it.attributes["org.gradle.usage"]?.endsWith("-api") == true
                Scope.RUNTIME -> it.attributes["org.gradle.usage"]?.endsWith("-runtime") == true
            }
        }.filter {
            !isGuava() || it.attributes["org.gradle.jvm.environment"]?.endsWith(context.settings.platform) == true
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
            createOrReuseDependency(context, it.group, it.module, it.version.requires)
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

    private fun resolveUsingPom(text: String, context: Context, resolutionLevel: ResolutionLevel) {
        val project = try {
            text.parsePom().resolve(context, resolutionLevel)
        } catch (e: Exception) {
            messages += Message(
                "Unable to parse pom file ${this.pom}",
                e.toString(),
                Severity.ERROR,
            )
            return
        }
        packaging = project.packaging
        (project.dependencies?.dependencies ?: listOf()).filter {
            when (context.settings.scope) {
                Scope.COMPILE -> it.scope in setOf(null, "compile")
                Scope.RUNTIME -> it.scope in setOf(null, "compile", "runtime")
            }
        }.filter {
            it.version != null && it.optional != true
        }.map {
            createOrReuseDependency(context, it.groupId, it.artifactId, it.version!!)
        }.let {
            children.addAll(it)
            state = resolutionLevel.state
        }
    }

    private fun Project.resolve(
        context: Context,
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
        val settings = context.settings
        val parentNode = parent?.let {
            createOrReuseDependency(context, it.groupId, it.artifactId, it.version)
        }
        val project = if (parentNode != null && (parentNode.pom.isDownloadedOrDownload(resolutionLevel, settings))) {
            val text = parentNode.pom.readText()
            val parentProject = text.parsePom().resolve(context, resolutionLevel, depth + 1, origin)
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
                    val dependency = createOrReuseDependency(context, it.groupId, it.artifactId, it.version)
                    if (dependency.pom.isDownloadedOrDownload(resolutionLevel, settings)) {
                        val text = dependency.pom.readText()
                        val dependencyProject = text.parsePom().resolve(context, resolutionLevel, depth + 1, origin)
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

    private fun DependencyFile.isDownloadedOrDownload(level: ResolutionLevel, settings: Settings) =
        isDownloaded(level, settings) || level == ResolutionLevel.NETWORK && download(settings)

    fun downloadDependencies(settings: Settings) {
        files.values
            .filter { !it.isDownloaded(ResolutionLevel.NETWORK, settings) }
            .forEach { it.download(settings) }
    }
}
