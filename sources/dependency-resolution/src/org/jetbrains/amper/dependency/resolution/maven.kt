/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.amper.dependency.resolution.metadata.json.AvailableAt
import org.jetbrains.amper.dependency.resolution.metadata.json.Capability
import org.jetbrains.amper.dependency.resolution.metadata.json.Dependency
import org.jetbrains.amper.dependency.resolution.metadata.json.Module
import org.jetbrains.amper.dependency.resolution.metadata.json.Variant
import org.jetbrains.amper.dependency.resolution.metadata.json.Version
import org.jetbrains.amper.dependency.resolution.metadata.json.parseMetadata
import org.jetbrains.amper.dependency.resolution.metadata.xml.Dependencies
import org.jetbrains.amper.dependency.resolution.metadata.xml.DependencyManagement
import org.jetbrains.amper.dependency.resolution.metadata.xml.Project
import org.jetbrains.amper.dependency.resolution.metadata.xml.expandTemplates
import org.jetbrains.amper.dependency.resolution.metadata.xml.parsePom
import org.jetbrains.amper.dependency.resolution.metadata.xml.plus
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.name
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Serves as a holder for a dependency defined by Maven coordinates, namely, group, module, and version.
 * While each node in a graph is expected to be unique, its [dependency] can be shared across other nodes
 * as long as their groups and modules match.
 * A version discrepancy might occur if a conflict resolution algorithm intervenes and is expected.
 *
 * The node doesn't do actual work but simply delegate to the [dependency] that can change over time.
 * This allows reusing dependency resolution results but still holding information about the origin.
 *
 * It's a responsibility of the caller to set a parent for this node if none was provided via the constructor.
 *
 * @see [ModuleDependencyNode]
 */
class MavenDependencyNode internal constructor(
    templateContext: Context,
    dependency: MavenDependency,
    parentNodes: List<DependencyNode> = emptyList(),
) : DependencyNode {

    constructor(
        templateContext: Context,
        group: String,
        module: String,
        version: String,
        parentNodes: List<DependencyNode> = emptyList(),
    ) : this(
        templateContext,
        createOrReuseDependency(templateContext, group, module, version),
        parentNodes,
    )

    @Volatile
    var dependency: MavenDependency = dependency
        set(value) {
            assert(group == value.group) { "Groups don't match. Expected: $group, actual: ${value.group}" }
            assert(module == value.module) { "Modules don't match. Expected: $module, actual: ${value.module}" }
            field = value
        }

    val group: String = dependency.group
    val module: String = dependency.module
    val version: String = dependency.version

    override val context: Context = templateContext.copyWithNewNodeCache(parentNodes)
    override val key: Key<*> = Key<MavenDependency>("$group:$module")
    override val children: List<DependencyNode> by PropertyWithDependency(
        value = listOf<MavenDependencyNode>(),
        dependency = listOf<DependencyNode>(),
        valueProvider = { thisRef ->
            thisRef.dependency.children.map { thisRef.context.getOrCreateNode(it, this) }
        },
        dependencyProvider = { thisRef ->
            thisRef.dependency.children
        }
    )
    override val messages: List<Message>
        get() = dependency.messages

    override suspend fun resolveChildren(level: ResolutionLevel) = dependency.resolveChildren(context, level)

    override suspend fun downloadDependencies() = dependency.downloadDependencies(context)

    override fun toString(): String = if (dependency.version == version) {
        dependency.toString()
    } else {
        "$group:$module:$version -> ${dependency.version}"
    }
}

/**
 * A lazy property that's recalculated if its dependency changes.
 */
class PropertyWithDependency<in T, out V, D>(
    @Volatile private var value: V,
    @Volatile private var dependency: D,
    val valueProvider: (T) -> V,
    val dependencyProvider: (T) -> D
) : ReadOnlyProperty<T, V> {
    override fun getValue(thisRef: T, property: KProperty<*>): V {
        val newDependency = dependencyProvider(thisRef)
        if (dependency != newDependency) {
            synchronized(this) {
                if (dependency != newDependency) {
                    dependency = newDependency
                    value = valueProvider(thisRef)
                }
            }
        }
        return value
    }
}

private fun createOrReuseDependency(
    context: Context,
    group: String,
    module: String,
    version: String
): MavenDependency = context.resolutionCache.computeIfAbsent(Key<MavenDependency>("$group:$module:$version")) {
    MavenDependency(context.settings.fileCache, group, module, version)
}

/**
 * An actual Maven dependency that can be resolved, that is, populated with children according to the requested
 * [ResolutionScope] and platform.
 * Its [resolve] method contains the resolution algorithm.
 *
 * @see [DependencyFile]
 */
class MavenDependency internal constructor(
    val fileCache: FileCache,
    val group: String,
    val module: String,
    val version: String
) {

    @Volatile
    var state: ResolutionState = ResolutionState.INITIAL
        private set

    @Volatile
    var variants: List<Variant> = listOf()
        internal set

    @Volatile
    var packaging: String? = null
        private set

    @Volatile
    var children: List<MavenDependency> = listOf()
        private set

    val messages: List<Message> = CopyOnWriteArrayList()

    private val mutex = Mutex()

    val metadata = getDependencyFile(this, getNameWithoutExtension(this), "module")
    val pom = getDependencyFile(this, getNameWithoutExtension(this), "pom")
    val files
        get() = buildList {
            variants.flatMap { it.files }.forEach {
                val nameWithoutExtension = it.url.substringBeforeLast('.')
                val extension = it.name.substringAfterLast('.')
                add(getDependencyFile(this@MavenDependency, nameWithoutExtension, extension))
            }
            packaging?.takeIf { it != "pom" }?.let {
                val nameWithoutExtension = getNameWithoutExtension(this@MavenDependency)
                val extension = if (it == "bundle") "jar" else it
                add(getDependencyFile(this@MavenDependency, nameWithoutExtension, extension))
                if (extension == "jar") {
                    add(getDependencyFile(this@MavenDependency, "$nameWithoutExtension-sources", extension))
                }
            }
        }

    override fun toString(): String = "$group:$module:$version"

    suspend fun resolveChildren(context: Context, level: ResolutionLevel) {
        if (state < level.state) {
            mutex.withLock {
                if (state < level.state) {
                    resolve(context, level)
                }
            }
        }
    }

    private suspend fun resolve(context: Context, level: ResolutionLevel) {
        val settings = context.settings
        // 1. Download pom.
        val pomText = if (pom.isDownloadedOrDownload(level, context)) {
            pom.readText()
        } else {
            messages.asMutable() += Message(
                "Pom required for $this",
                settings.repositories.toString(),
                if (level == ResolutionLevel.NETWORK) Severity.ERROR else Severity.WARNING,
            )
            null
        }
        // 2. If pom is missing or mentions metadata, use it.
        if (pomText == null || pomText.contains("do_not_remove: published-with-gradle-metadata")) {
            if (metadata.isDownloadedOrDownload(level, context)) {
                resolveUsingMetadata(context, level)
                return
            }
            if (pomText != null) {
                messages.asMutable() += Message(
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

    private fun List<Variant>.filterWithFallbackPlatform(context: Context) : List<Variant> {
        val platformVariants = this.filter { context.settings.platform.matches(it) }
        return when {
            platformVariants.withoutDocumentation.isNotEmpty()
                    || context.settings.platform.fallback == null -> platformVariants
            else -> this.filter { context.settings.platform.fallback.matches(it) }
        }
    }

    private fun List<Variant>.filterMultipleVariantsByUnusedAttributes(context: Context) : List<Variant> {
        return when {
            (this.withoutDocumentation.size == 1) -> this
            else -> {
                val usedAttributes = setOf(
                  "org.gradle.category",
                  "org.gradle.usage",
                  "org.jetbrains.kotlin.native.target",
                  "org.jetbrains.kotlin.platform.type",
                )
                val minUnusedAttrsCount = minOfOrNull { v ->
                    v.attributes.count { it.key !in usedAttributes }
                }
                this.filter { v -> v.attributes.count { it.key !in usedAttributes } == minUnusedAttrsCount }
                    .let {
                        if (it.withoutDocumentation.size == 1) {
                            it
                        } else {
                            this
                        }
                    }
            }
        }
    }

    private suspend fun resolveUsingMetadata(context: Context, level: ResolutionLevel) {
        val module = try {
            metadata.readText().parseMetadata()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messages.asMutable() += Message(
                "Unable to parse metadata file $metadata",
                e.toString(),
                Severity.ERROR,
                e,
            )
            return
        }

        val initiallyFilteredVariants = module
            .variants
            // todo (AB) : Why filtering against capabilities?
            .filter { it.capabilities.isEmpty() || it.capabilities == listOf(toCapability()) || it.isOneOfExceptions() }
            .filter { context.settings.nativeTargetMatches(it) }
            .filter { context.settings.scope.matches(it) }

        val validVariants = initiallyFilteredVariants
            .filterWithFallbackPlatform(context)
            .filterMultipleVariantsByUnusedAttributes(context)

        return validVariants.also {
            variants = it
            if (it.withoutDocumentation.size > 1) {
                messages.asMutable() += Message(
                    "More than a single variant provided",
                    it.joinToString { it.name },
                    Severity.WARNING,
                )
            }
        }.flatMap {
            it.dependencies + listOfNotNull(it.`available-at`?.asDependency())
        }.mapNotNull { dependency ->
            // todo (AB) : 'strictly' should have special support (we have to take this into account during conflict resolution)
            val version = dependency.version.strictly ?: dependency.version.requires ?: dependency.version.prefers
            if (version == null) {
                reportDependencyVersionResolutionFailure(dependency, module)
                return@mapNotNull null
            }
            createOrReuseDependency(context, dependency.group, dependency.module, version)
        }.let {
            children = it
            state = level.state
        }
    }

    private fun reportDependencyVersionResolutionFailure(dependency: Dependency, module: Module) {
        messages.asMutable() += Message(
            "Module ${module.component.group}:${module.component.module}:${module.component.version} " +
                    "depends on ${dependency.group}:${dependency.module}, but version of the dependency could not be resolved: " +
                    "neither 'requires' nor 'prefers' attributes are defined",
            severity = Severity.ERROR
        )
    }

    private fun Variant.isOneOfExceptions() = isKotlinException() || isGuavaException()

    private fun Variant.isKotlinException() =
        isKotlinTestJunit() && capabilities.sortedBy { it.name } == listOf(
            Capability(group, "kotlin-test-framework-impl", version),
            toCapability()
        )

    private fun isKotlinTestJunit() =
        group == "org.jetbrains.kotlin" && (module in setOf("kotlin-test-junit", "kotlin-test-junit5"))

    private fun Variant.isGuavaException() =
        isGuava() && capabilities.sortedBy { it.name } == listOf(
            Capability("com.google.collections", "google-collections", version),
            toCapability()
        ) && attributes["org.gradle.jvm.environment"] == when (version.substringAfterLast('-')) {
            "android" -> "android"
            "jre" -> "standard-jvm"
            else -> null
        }

    private fun isGuava() = group == "com.google.guava" && module == "guava"

    private fun MavenDependency.toCapability() = Capability(group, module, version)

    private fun String.matches(variant: Variant) =
        variant.attributes["org.jetbrains.kotlin.platform.type"]?.let { it == this } ?: true

    private fun Settings.nativeTargetMatches(variant: Variant) =
        variant.attributes["org.jetbrains.kotlin.platform.type"] != PlatformType.NATIVE.value
                || variant.attributes["org.jetbrains.kotlin.native.target"] == nativeTarget

    private fun AvailableAt.asDependency() = Dependency(group, module, Version(version))

    private suspend fun resolveUsingPom(text: String, context: Context, level: ResolutionLevel) {
        val project = try {
            text.parsePom().resolve(context, level)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messages.asMutable() += Message(
                "Unable to parse pom file ${this.pom}",
                e.toString(),
                Severity.ERROR,
                e,
            )
            return
        }
        packaging = project.packaging ?: "jar"
        (project.dependencies?.dependencies ?: listOf()).filter {
            context.settings.scope.matches(it)
        }.filter {
            it.version != null && it.optional != true
        }.map {
            createOrReuseDependency(context, it.groupId, it.artifactId, it.version!!)
        }.let {
            children = it
            state = level.state
        }
    }

    /**
     * Resolves a Maven project by recursively substituting references to parent projects and templates
     * with actual values.
     * Additionally, dependency versions are defined using dependency management.
     */
    private suspend fun Project.resolve(
        context: Context,
        resolutionLevel: ResolutionLevel,
        depth: Int = 0,
        origin: Project = this
    ): Project {
        if (depth > 10) {
            messages.asMutable() += Message(
                "Project ${origin.name} has more than ten ancestors",
                severity = Severity.WARNING,
            )
            return this
        }
        val parentNode = parent?.let {
            createOrReuseDependency(context, it.groupId, it.artifactId, it.version)
        }
        val project = if (parentNode != null && (parentNode.pom.isDownloadedOrDownload(resolutionLevel, context))) {
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
                    if (dependency.pom.isDownloadedOrDownload(resolutionLevel, context)) {
                        val text = dependency.pom.readText()
                        val dependencyProject = text.parsePom().resolve(context, resolutionLevel, depth + 1, origin)
                        dependencyProject.dependencyManagement?.dependencies?.dependencies?.let {
                            return@flatMap it
                        }
                    }
                }
                return@flatMap listOf(it)
            }
        val dependencies = project.dependencies
            ?.dependencies
            ?.map {
                if (it.version == null) {
                    val dependency = dependencyManagement?.find { dep ->
                        dep.groupId == it.groupId && dep.artifactId == it.artifactId
                    }
                    dependency?.version
                        ?.let { version -> it.copy(version = version) }
                        ?.let { return@map it }
                }
                return@map it
            }
            ?.map { it.expandTemplates(project) }
        return project.copy(
            dependencies = dependencies?.let { Dependencies(it) },
            dependencyManagement = dependencyManagement?.let { DependencyManagement(Dependencies(it)) },
        )
    }

    private suspend fun DependencyFile.isDownloadedOrDownload(level: ResolutionLevel, context: Context) =
        isDownloaded() && hasMatchingChecksum(level, context) || level == ResolutionLevel.NETWORK && download(context)

    private val Collection<Variant>.withoutDocumentation: Collection<Variant> get() =
        filterNot { it.attributes["org.gradle.category"] == "documentation" }

    suspend fun downloadDependencies(context: Context) {
        files
            .filter {
                // filter out sources/javadocs if those are not requested in settings
                context.settings.downloadSources
                        || (!"${it.nameWithoutExtension}.${it.extension}".let { name -> name.endsWith("-sources.jar") || name.endsWith("-javadoc.jar") } )
            }
            .filter { !(it.isDownloaded() && it.hasMatchingChecksum(ResolutionLevel.NETWORK, context)) }
            .forEach { it.download(context) }
    }
}

internal fun <E> List<E>.asMutable(): MutableList<E> = this as MutableList<E>
