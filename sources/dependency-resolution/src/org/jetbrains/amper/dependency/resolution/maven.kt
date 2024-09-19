/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.amper.concurrency.computeHash
import org.jetbrains.amper.concurrency.produceFileWithDoubleLockAndHash
import org.jetbrains.amper.dependency.resolution.metadata.json.module.AvailableAt
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Capability
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Dependency
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Module
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Version
import org.jetbrains.amper.dependency.resolution.metadata.json.module.parseMetadata
import org.jetbrains.amper.dependency.resolution.metadata.json.projectStructure.KotlinProjectStructureMetadata
import org.jetbrains.amper.dependency.resolution.metadata.json.projectStructure.SourceSet
import org.jetbrains.amper.dependency.resolution.metadata.json.projectStructure.parseKmpLibraryMetadata
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
 * @see [DependencyNodeHolder]
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
        templateContext.createOrReuseDependency(group, module, version),
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
    override val key: Key<MavenDependency> = Key<MavenDependency>("$group:$module")
    override val children: List<DependencyNode> by PropertyWithDependencyGeneric(
        dependencyProviders = listOf(
            { thisRef: MavenDependencyNode -> thisRef.dependency.children },
            { thisRef: MavenDependencyNode -> thisRef.dependency.dependencyConstraints }
        ),
        valueProvider = { dependencies ->
            val children = dependencies[0] as List<MavenDependency>
            val dependencyConstraints = dependencies[1] as List<MavenDependencyConstraint>
            children.mapNotNull {
                context
                    .getOrCreateNode(it, this)
                    // skip children that form cyclic dependencies
                    .takeIf { !it.isDescendantOf(it) }
            } + dependencyConstraints.map {
                context.getOrCreateConstraintNode(it, this)
            }
        },
    )

    override val messages: List<Message>
        get() = dependency.messages

    override suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean) {
        if (transitive) {
            dependency.resolveChildren(context, level)
        }
    }

    override suspend fun downloadDependencies(downloadSources: Boolean) = dependency.downloadDependencies(context, downloadSources)

    override fun toString(): String = if (dependency.version == version) {
        dependency.toString()
    } else {
        "$group:$module:$version -> ${dependency.version}"
    }

    private fun DependencyNode.isDescendantOf(parent: DependencyNode): Boolean {
        return parents.any { it.key == parent.key }
                || parents.any { it.isDescendantOf(parent) }
    }
}

class UnresolvedMavenDependencyNode(
    val coordinates: String,
    templateContext: Context,
    parentNodes: List<DependencyNode> = emptyList()
) : DependencyNode {
    override val context = templateContext.copyWithNewNodeCache(parentNodes)
    override val key: Key<*> = Key<UnresolvedMavenDependencyNode>(coordinates)
    override val children: List<DependencyNode> = emptyList()
    override val messages: List<Message> = listOf(getMessage())
    override suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean) { }
    override suspend fun downloadDependencies(downloadSources: Boolean) { }

    private fun getMessage(): Message {
        val gradleCoordinates = gradlePrefixToSuffixMapper.entries.firstOrNull { coordinates.startsWith("${it.key}(") }
        return if (gradleCoordinates != null) {
            return Message("Dependency coordinates in a Gradle format are not supported. Please change it to an Amper-compatible format.", severity = Severity.ERROR)
        } else {
            Message("Unresolved dependency coordinates", severity = Severity.ERROR)
        }
    }

    companion object {
        val gradlePrefixToSuffixMapper = mapOf(
            Pair("api", ""),
            Pair("implementation", ""),
            Pair("testImplementation", ""),
            Pair("compileOnly", "compile-only"),
            Pair("compileOnlyApi", "compile-only"),
            Pair("testCompileOnly", "compile-only"),
            Pair("runtimeOnly", "runtime-only"),
            Pair("testRuntimeOnly", "runtime-only")
        )
    }
}

class MavenDependencyConstraintNode internal constructor(
    templateContext: Context,
    dependencyConstraint: MavenDependencyConstraint,
    parentNodes: List<DependencyNode> = emptyList(),
) : DependencyNode {

    @Volatile
    var dependencyConstraint: MavenDependencyConstraint = dependencyConstraint
        set(value) {
            assert(group == value.group) { "Groups don't match. Expected: $group, actual: ${value.group}" }
            assert(module == value.module) { "Modules don't match. Expected: $module, actual: ${value.module}" }
            field = value
        }

    val group: String = dependencyConstraint.group
    val module: String = dependencyConstraint.module
    val version: Version = dependencyConstraint.version

    override val context: Context = templateContext.copyWithNewNodeCache(parentNodes)
    override val key: Key<*> = Key<MavenDependency>("$group:$module") // reusing the same key as MavenDependencyNode
    override val children: List<DependencyNode> = emptyList()
    override val messages: List<Message> = emptyList()

    override suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean) { }

    override suspend fun downloadDependencies(downloadSources: Boolean) { }

    override fun toString(): String = if (dependencyConstraint.version == version) {
        "$group:$module:${version.resolve()}"
    } else {
        "$group:$module:${version.asString()} -> ${dependencyConstraint.version.asString()}"
    }

    private fun Version.asString(): String =
        strictly?.let { "{strictly $strictly} -> $strictly" }
            ?: resolve()
            ?: "null"
}

private typealias DependencyProvider<T> = (T) -> Any?

/**
 * A lazy property that's recalculated if its dependency changes.
 */
private class PropertyWithDependencyGeneric<T, out V: Any>(
    val dependencyProviders: List<DependencyProvider<T>>,
    val valueProvider: (List<Any?>) -> V,
) : ReadOnlyProperty<T, V> {

    @Volatile private lateinit var dependencies: List<Any?>
    @Volatile private lateinit var value: V

    override fun getValue(thisRef: T, property: KProperty<*>): V {
        val newDependencies = dependencyProviders.map { it.invoke(thisRef) }
        if (shouldRecalculate(newDependencies)) {
            synchronized(this) {
                if (shouldRecalculate(newDependencies)) {
                    dependencies = newDependencies
                    value = valueProvider(dependencies)
                }
            }
        }
        return value
    }

    private fun shouldRecalculate(newDependencies: List<Any?>): Boolean =
        !this::value.isInitialized || !this::dependencies.isInitialized || newDependencies.anyIndexed { index, dep -> dep != dependencies[index] }

    private inline fun <T> Iterable<T>.anyIndexed(action: (index: Int, T) -> Boolean): Boolean {
        var index = 0
        for (item in this) {
            if (action(index++, item)) return true
        }
        return false
    }
}

fun Context.createOrReuseDependency(
    group: String,
    module: String,
    version: String
): MavenDependency = this.resolutionCache.computeIfAbsent(Key<MavenDependency>("$group:$module:$version")) {
    MavenDependency(this.settings, group, module, version)
}

internal fun Context.createOrReuseDependencyConstraint(
    group: String,
    module: String,
    version: Version
): MavenDependencyConstraint = this.resolutionCache.computeIfAbsent(Key<MavenDependencyConstraint>("$group:$module:$version")) {
    MavenDependencyConstraint(group, module, version)
}

data class MavenDependencyConstraint(
    val group: String,
    val module: String,
    val version: Version
)

/**
 * An actual Maven dependency that can be resolved, that is, populated with children according to the requested
 * [ResolutionScope] and platform.
 * That means MavenDependency is bound to dependency resolution context, i.e., the instance of the class resolved for one context
 * could not be reused in another one.
 * Its [resolve] method contains the resolution algorithm.
 *
 * @see [DependencyFile]
 */
class MavenDependency internal constructor(
    val settings: Settings,
    val group: String,
    val module: String,
    val version: String
) {

    @Volatile
    var state: ResolutionState = ResolutionState.INITIAL
        private set

    @Volatile
    internal var variants: List<Variant> = listOf()
        internal set

    @Volatile

    internal var sourceSetsFiles: List<DependencyFile> = listOf()
        private set

    @Volatile
    var packaging: String? = null
        private set

    @Volatile
    var children: List<MavenDependency> = listOf()
        private set

    @Volatile
    internal var dependencyConstraints: List<MavenDependencyConstraint> = listOf()
        private set

    val messages: List<Message> = CopyOnWriteArrayList()

    private val mutex = Mutex()

    internal val moduleFile = getDependencyFile(this, getNameWithoutExtension(this), "module")
    val pom = getDependencyFile(this, getNameWithoutExtension(this), "pom")

    fun files(withSources: Boolean = false) =
        if (withSources)
            _files
        else
            _filesWithoutSources.filterNot { it.fileName.endsWith("-sources.jar") || it.fileName.endsWith("-javadoc.jar") }

    private val _files: List<DependencyFile> by filesProvider(withSources = true)
    private val _filesWithoutSources: List<DependencyFile> by filesProvider(withSources = false)

    private fun filesProvider(withSources: Boolean) =
        PropertyWithDependencyGeneric(
            dependencyProviders = listOf(
                { thisRef: MavenDependency -> thisRef.variants },
                { thisRef: MavenDependency -> thisRef.packaging },
                { thisRef: MavenDependency -> thisRef.sourceSetsFiles }
            ),
            valueProvider = { dependencies ->
                val variants = dependencies[0] as List<Variant>
                val packaging = dependencies[1] as String?
                val sourceSetsFiles = dependencies[2] as List<DependencyFile>
                buildList {
                    variants
                        .let { if (withSources) it else it.withoutDocumentationAndMetadata }
                        .flatMap { it.files }
                        .forEach {
                            add(getDependencyFile(this@MavenDependency, it))
                        }
                    packaging?.takeIf { it != "pom" }?.let {
                        val nameWithoutExtension = getNameWithoutExtension(this@MavenDependency)
                        val extension = if (it == "bundle") "jar" else it
                        add(getDependencyFile(this@MavenDependency, nameWithoutExtension, extension))
                        if (extension == "jar" && withSources) {
                            add(getDependencyFile(this@MavenDependency, "$nameWithoutExtension-sources", extension))

                        }
                    }
                    sourceSetsFiles.let { addAll(it) }
                }
            },
        )

    /**
     * The repository module/pom file was downloaded from.
     * If we download module/pom file from some repository,
     * then it would be optimal to try downloading resolved variants and hashes from the same repository first as well,
     * instead of traversing the list in an original order.
     */
    @Volatile
    internal var repository: Repository? = null
        set(value) {
            if (field == null && value != null) {
                field = value
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
            if (level != ResolutionLevel.NETWORK) {
                messages.asMutable() += Message(
                    "Pom was not found for $this",
                    settings.repositories.toString(),
                    Severity.WARNING,
                )
            }
            null
        }
        // 2. If pom is missing or mentions metadata, use it.
        if (pomText == null || pomText.contains("do_not_remove: published-with-gradle-metadata")) {
            if (moduleFile.isDownloadedOrDownload(level, context)) {
                resolveUsingMetadata(context, level)
                return
            }
            if (pomText != null) {
                if (level != ResolutionLevel.NETWORK) {
                    messages.asMutable() += Message(
                        "Pom is resolved, but metadata was not found for $this",
                        context.settings.repositories.toString(),
                        Severity.WARNING,
                    )
                }
            }
        }
        // 3. If can't use metadata, use pom.
        if (pomText != null) {
            resolveUsingPom(pomText, context, level)
        }
    }

    private fun List<Variant>.filterWithFallbackScope(scope: ResolutionScope): List<Variant> {
        val scopeVariants = this.filter { scope.matches(it) }
        return scopeVariants.takeIf { it.withoutDocumentationAndMetadata.isNotEmpty() }
            ?: scope.fallback()?.let { fallbackScope ->
                this.filter { fallbackScope.matches(it) }.takeIf { it.withoutDocumentationAndMetadata.isNotEmpty() }
            }
            ?: scopeVariants
    }

    private fun List<Variant>.filterWithFallbackPlatform(platform: ResolutionPlatform): List<Variant> {
        val platformVariants = this.filter { platform.type.matches(it) }
        return when {
            platformVariants.withoutDocumentationAndMetadata.isNotEmpty()
                    || platform.type.fallback == null -> platformVariants

            else -> this.filter { platform.type.fallback.matches(it) }
        }
    }

    private fun List<Variant>.filterMultipleVariantsByUnusedAttributes(): List<Variant> {
        return when {
            (this.withoutDocumentationAndMetadata.size == 1) -> this
            else -> {
                val usedAttributes = setOf(
                    "org.gradle.category",
                    "org.gradle.usage",
                    "org.jetbrains.kotlin.native.target",
                    "org.jetbrains.kotlin.platform.type",
                )
                val minUnusedAttrsCount = this.minOfOrNull { v ->
                    v.attributes.count { it.key !in usedAttributes }
                }
                this.filter { v -> v.attributes.count { it.key !in usedAttributes } == minUnusedAttrsCount}
                    .let {
                        if (it.withoutDocumentationAndMetadata.size == 1) {
                            it
                        } else {
                            this
                        }
                    }
            }
        }
    }

    private suspend fun resolveUsingMetadata(context: Context, level: ResolutionLevel) {
        val moduleMetadata = parseModuleMetadata(context, level) ?: return

        if (context.settings.platforms.isEmpty()) {
            throw AmperDependencyResolutionException("Target platform is not specified.")
        } else if (context.settings.platforms.singleOrNull() == ResolutionPlatform.COMMON) {
            throw AmperDependencyResolutionException(
                "Dependency resolution can not be run for COMMON platform. " +
                        "Set of actual target platforms should be specified."
            )
        }

        if (context.settings.platforms.size == 1) {
            val platform = context.settings.platforms.single()
            val validVariants = resolveVariants(moduleMetadata, context.settings, platform)

            validVariants.also {
                variants = it
                if (it.withoutDocumentationAndMetadata.size > 1) {
                    messages.asMutable() += Message(
                        "More than a single variant provided",
                        it.joinToString { it.name },
                        Severity.WARNING,
                    )
                }
            }
            // One platform case
            validVariants
                .withoutDocumentationAndMetadata
                .let { variants ->
                    variants.flatMap {
                        it.dependencies(context, moduleMetadata, level) + listOfNotNull(it.`available-at`?.asDependency())
                    }.mapNotNull {
                        it.toMavenDependency(context, moduleMetadata)
                    }.let {
                        children = it
                    }

                    variants.flatMap {
                        it.dependencyConstraints
                    }.mapNotNull {
                        it.toMavenDependencyConstraint(context)
                    }.let {
                        dependencyConstraints = it
                    }
                }
        } else {
            // Multiplatform case
            val processedAsSpecialCase = processSpecialKmpLibraries(context, moduleMetadata, level)
            if (processedAsSpecialCase) {
                return
            }

            val (kotlinMetadataVariant, kmpMetadataFile) =
                detectKotlinMetadataLibrary(context, ResolutionPlatform.COMMON, moduleMetadata, level)
                    ?: return  // children list is empty in case kmp common variant is not resolved

            resolveKmpLibrary(kmpMetadataFile, context, moduleMetadata, level, kotlinMetadataVariant)
        }

        state = level.state
    }

    /**
     * Some pretty basic libraries that are used in KMP world mimic for a pure JVM libraries,
     * those cases should be processed in a custom way.
     *
     * If this method returns true, it means such a library was detected and processed (library dependencies are registered to graph),
     * no further processing by a usual way is needed.
     *
     * @return true, in case Kmp library that needs special treatment was detected and processed, false - otherwise
     */
    private suspend fun processSpecialKmpLibraries(context: Context, moduleMetadata: Module, level: ResolutionLevel): Boolean {
        if (isKotlinTestAnnotationsCommon()) {
            moduleMetadata
                .variants
                .let {
                    it.flatMap {
                        it.dependencies(context, moduleMetadata, level)
                    }.mapNotNull {
                        it.toMavenDependency(context, moduleMetadata)
                    }.let {
                        children = it
                    }

                    it.flatMap {
                        it.dependencyConstraints
                    }.mapNotNull {
                        it.toMavenDependencyConstraint(context)
                    }.let {
                        dependencyConstraints = it
                    }
                }
            return true
        }

        return false
    }

    private fun Dependency.toMavenDependencyConstraint(context: Context): MavenDependencyConstraint? {
        return version?.let { context.createOrReuseDependencyConstraint(group, module, version) }
    }

    private fun Dependency.toMavenDependency(context: Context, errorMessageBuilder: (String) -> String): MavenDependency? {
        val resolvedVersion = resolveVersion(errorMessageBuilder)
        return resolvedVersion?.let { context.createOrReuseDependency(group, module, resolvedVersion) }
    }

    private fun Dependency.toMavenDependency(context: Context, module: Module): MavenDependency? {
        return toMavenDependency(context) { versionError ->
            "Module ${module.component.group}:${module.component.module}:${module.component.version} " +
                    "depends on ${this@toMavenDependency.group}:${this@toMavenDependency.module}, but its version could not be resolved:" +
                    " $versionError"
        }
    }

    private suspend fun Variant.bomDependencyConstraints(context: Context, module: Module, level: ResolutionLevel): List<MavenDependencyConstraint> =
        dependencies.mapNotNull {
            if (!it.isBom()) {
                null
            } else {
                withResolvedVersion(it)
                    .takeIf { it.version != null  }
                    ?.toMavenDependency(context, module)
                    ?.let {
                        it.resolveChildren(context, level)
                        it.dependencyConstraints
                    }
            }
        }.flatten()

    private suspend fun Variant.dependencies(context: Context, module: Module, level: ResolutionLevel): List<Dependency> =
        dependencies.map {
            withResolvedVersion(it) { bomDependencyConstraints(context, module, level) }
        }

    /**
     * Resolve a dependency version.
     *
     * Usually it is defined right on a dependency declaration, and in this case, we just return the dependency as is.
     * If it is not defined on a dependency, then we try to resolve it via dependency constraints
     * declared for current node metadata.
     * If a version is still not resolved (neither directly nor via constraints),
     * then BOM dependencies are resolved, and the version is taken from them (if any).
     *
     * BOM dependencies are lazily resolved at this stage if a version can't be resolved via current node metadata only.
     */
    private suspend fun Variant.withResolvedVersion(dep: Dependency, bomDependencyConstraints: suspend () -> List<MavenDependencyConstraint> = { emptyList() }): Dependency =
        if (dep.version != null) {
            dep
        } else {
            val versionFromConstraint = dependencyConstraints
                .firstOrNull { dep.module == it.module && dep.group == it.group && it.version != null }
                ?.version
                ?: bomDependencyConstraints()
                    .firstOrNull { dep.module == it.module && dep.group == it.group }
                    ?.version
            if (versionFromConstraint != null) {
                dep.copy(version = versionFromConstraint)
            } else {
                dep
            }
        }

    private fun Dependency.resolveVersion(errorMessageBuilder: (String) -> String): String? {
        return if (version == null) {
            messages.asMutable() += Message(
                text = errorMessageBuilder("Attribute 'version' is not defined"),
                severity = Severity.ERROR
            )
            null
        } else {
            val resolvedVersion = version.resolve()
            if (resolvedVersion == null) {
                messages.asMutable() += Message(
                    text = errorMessageBuilder("neither 'requires' nor 'prefers' nor 'strictly' attributes are defined"),
                    severity = Severity.ERROR
                )
                return null
            }
            resolvedVersion
        }
    }

    private suspend fun parseModuleMetadata(context: Context, level: ResolutionLevel): Module? {
        if (moduleFile.isDownloadedOrDownload(level, context)) {
            try {
                return moduleFile.readText().parseMetadata()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.asMutable() += Message(
                    "Unable to parse metadata file $moduleFile",
                    e.toString(),
                    Severity.ERROR,
                    e,
                )
            }
        } else {
            messages.asMutable() += Message(
                "module file was not downloaded for $this",
                context.settings.repositories.toString(),
                if (level.state == ResolutionState.RESOLVED) Severity.ERROR else Severity.WARNING,
            )
        }
        return null
    }

    private suspend fun detectKotlinMetadataLibrary(
        context: Context,
        platform: ResolutionPlatform,
        moduleMetadata: Module?,
        level: ResolutionLevel
    ): Pair<Variant, DependencyFile>? {
        val resolvedModuleMetadata = moduleMetadata
            ?: parseModuleMetadata(context, level)
            ?: return null

        val kotlinMetadataVariant = getKotlinMetadataVariant(resolvedModuleMetadata.variants, platform)
            ?: return null  // children list is empty in case kmp common variant is not resolved
        val kotlinMetadataFile = getKotlinMetadataFile(kotlinMetadataVariant)
            ?: return null  // children list is empty in case kmp common variant file is not resolved

        val kmpMetadataDependencyFile = getDependencyFile(this, kotlinMetadataFile)

        return (kotlinMetadataVariant to kmpMetadataDependencyFile).takeIf {
            it.second.isDownloadedOrDownload(
                level,
                context
            )
        }
            ?: run {
                messages.asMutable() += Message(
                    "Kotlin metadata file ${kmpMetadataDependencyFile.nameWithoutExtension}.${kmpMetadataDependencyFile.extension} is required for $this",
                    severity = if (level == ResolutionLevel.NETWORK) Severity.ERROR else Severity.WARNING,
                )
                null
            }
    }

    private suspend fun resolveKmpLibrary(
        kmpMetadataFile: DependencyFile,
        context: Context,
        moduleMetadata: Module,
        level: ResolutionLevel,
        kotlinMetadataVariant: Variant
    ) {
        val kmpMetadata = kmpMetadataFile.getPath()?.let {
            readJarEntry(it, "META-INF/kotlin-project-structure-metadata.json")
        } ?: run {
            messages.asMutable() += Message(
                "Can't resolve common library: META-INF/kotlin-project-structure-metadata.json is not found inside ${kmpMetadataFile.fileName}",
                severity = Severity.ERROR
            )
            return
        }

        val kotlinProjectStructureMetadata = kmpMetadata.parseKmpLibraryMetadata()

        val allPlatformsVariants = context.settings.platforms.flatMap {
            resolveVariants(moduleMetadata, context.settings, it).withoutDocumentationAndMetadata
        }.associateBy { it.name }

        // Selecting source sets related to target platforms (intersection).
        val sourceSetsIntersection = kotlinProjectStructureMetadata.projectStructure.variants
            .filter { it.name in (allPlatformsVariants.keys + allPlatformsVariants.keys.map { it.removeSuffix("-published") }) }
            .map { it.sourceSet.toSet() }
            .let {
                if (it.isEmpty()) emptySet() else it.reduce { l1, l2 -> l1.intersect(l2) }
            }

        // Transforming it right here, since it doesn't require network access in most cases.
        sourceSetsFiles = coroutineScope {
            kotlinProjectStructureMetadata.projectStructure.sourceSets
                .filter { it.name in sourceSetsIntersection }
                .map {
                    async(Dispatchers.IO) {
                        it.toDependencyFile(
                            kmpMetadataFile,
                            moduleMetadata,
                            kotlinProjectStructureMetadata,
                            context,
                            level
                        )
                    }
                }
        }.awaitAll()
            .mapNotNull { it }

        // Find source sets dependencies
        children = kotlinProjectStructureMetadata.projectStructure.sourceSets
            .filter { it.name in sourceSetsIntersection }
            .flatMap { it.moduleDependency }
            .toSet()
            .mapNotNull { rawDep ->
                val parts = rawDep.split(":")
                if (parts.size != 2) {
                    messages.asMutable() += Message(
                        "Kotlin library file ${group}:${module}:${version} depends on $rawDep that has unexpected coordinates format",
                        severity = Severity.ERROR,
                    )
                    null
                } else {
                    val dependencyGroup = parts[0]
                    val dependencyModule = parts[1]
                    kotlinMetadataVariant.dependencies(context, moduleMetadata, level)
                        .firstOrNull { it.group == dependencyGroup && it.module == dependencyModule }
                        ?.toMavenDependency(context) { versionErrorMessage ->
                            "Kotlin library file ${group}:${module}:${version} depends on $rawDep," +
                                    " but its version could not be determined from corresponding module file, " +
                                    " $versionErrorMessage"
                        }
               }
            }

        // todo (AB) : take kotlinMetadataVariant.dependencyConstraints into account as well? What subset on constraints should be taken into account?
    }

    private fun getKotlinMetadataFile(kotlinMetadataVariant: Variant) =
        kotlinMetadataVariant.files.singleOrNull()
            ?: run {
                messages.asMutable() += Message(
                    "Kotlin metadata file is not resolved for maven dependency ${group}:${module}:${version},",
                    severity = Severity.ERROR,
                )
                null
            }

    private fun getKotlinMetadataVariant(
        validVariants: List<Variant>,
        platform: ResolutionPlatform
    ): Variant? {
        if (this.isKotlinTestAnnotations()) return null
        val metadataVariants = validVariants.filter { it.isKotlinMetadata(platform) }
        return metadataVariants.firstOrNull() ?: run {
            messages.asMutable() += Message(
                "More than a single variant provided for multiplatform dependency ${group}:${module}:${version}, " +
                        "but kotlin metadata is not found (none of variants has attribute 'org.gradle.usage' equal to 'kotlin-metadata')",
                severity = Severity.ERROR,
            )

            null
        }
    }

    // todo (AB) : All this logic might be wrapped into ExecuteOnChange in order to skip metadata resolution in case targetFile exists already
    private suspend fun SourceSet.toDependencyFile(
        kmpMetadataFile: DependencyFile,
        moduleMetadata: Module,
        kotlinProjectStructureMetadata: KotlinProjectStructureMetadata,
        context: Context,
        level: ResolutionLevel
    ): DependencyFile? {
        val group = kmpMetadataFile.dependency.group
        val module = kmpMetadataFile.dependency.module
        val version = kmpMetadataFile.dependency.version
        // kmpMetadataFile hash
        val sha1 = kmpMetadataFile.getOrDownloadExpectedHash(
            "sha1", null, context.settings.progress, context.resolutionCache, level
        )
            ?: kmpMetadataFile.getPath()?.let {
                computeHash(it,"sha1").hash
            }
            ?: run {
                messages.asMutable() += Message(
                    "Kotlin metadata file hash sha1 could not be resolved for maven dependency ${group}:${module}:${version},",
                    severity = if (level == ResolutionLevel.NETWORK) Severity.ERROR else Severity.INFO,
                )
                return null
            }

        val sourceSetName = name

        val kmpLibraryWithSourceSet = resolveKmpLibraryWithSourceSet(
            sourceSetName, kmpMetadataFile, context, kotlinProjectStructureMetadata, moduleMetadata, level
        )
            ?: run {
                logger.debug("SourceSet $sourceSetName for kotlin multiplatform library ${kmpMetadataFile.fileName} is not found")
                return null
            }

        val sourceSetFile = DependencyFile(
            kmpMetadataFile.dependency,
            "${kmpMetadataFile.dependency.module}-$sourceSetName",
            "klib",
            kmpSourceSet = sourceSetName
        )

        val targetFileName = "$module-$sourceSetName-$version.klib"

        val targetPath = kmpMetadataFile.dependency.settings.fileCache.amperCache
            .resolve("kotlin/kotlinTransformedMetadataLibraries/")
            .resolve(group)
            .resolve(module)
            .resolve(version)
            .resolve(sha1)
            .resolve(targetFileName)

        produceFileWithDoubleLockAndHash(
            target = targetPath,
            tempDir = { with(sourceSetFile) { getTempDir() } },
        ) { _, fileChannel ->
            try {
                copyJarEntryDirToJar(fileChannel, sourceSetName, kmpLibraryWithSourceSet)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.asMutable() += Message(
                    "Failed repackaging kotlin multiplatform library ${kmpLibraryWithSourceSet.name}",
                    severity = Severity.ERROR,
                    exception = e
                )
                false
            }
        }

        sourceSetFile.onFileDownloaded(targetPath)
        return sourceSetFile
    }

    /**
     * This method return kotlin metadata library that contains given sourceSet.
     *
     * Usually, a kotlin metadata library contains both:
     * - sourceSets' descriptor: META-INF/kotlin-project-structure-metadata.json
     * - and sourceSets itself
     *
     * And in that case,
     * a path of the given kmpMetadataFile (representing the kotlin metadata library) is simply returned.
     *
     * But iOS sourceSet might be missing (for historical reasons);
     * in that case, sourceSets are stored in platform-specific kotlin metadata variants.
     * This method resolves such a platform-specific library and returns its path.
     *
     *  For example, let's consider library
     *  org.jetbrains.compose.ui:ui-uikit:1.6.10
     *
     *  Its kotlin metadata variant defines sourceSet 'uikitMain', but it doesn't include sourceSet itself.
     *  (https://maven.pkg.jetbrains.space/public/p/compose/dev/org/jetbrains/compose/ui/ui-uikit/1.6.10/ui-uikit-1.6.10.jar),
     *
     *  Instead, sourceSet 'uikitMain' is included in the kotlin metadata variant of
     *  EACH platform-specific dependency of the ui-uikit common library
     *  (for instance, in org.jetbrains.compose.ui:ui-uikit-uikitarm64:1.6.10)
     *  (https://maven.pkg.jetbrains.space/public/p/compose/dev/org/jetbrains/compose/ui/ui-uikit-uikitarm64/1.6.10/ui-uikit-uikitarm64-1.6.10-metadata.jar)
     */
    private suspend fun resolveKmpLibraryWithSourceSet(
        sourceSetName: String,
        kmpMetadataFile: DependencyFile,
        context: Context,
        kotlinProjectStructureMetadata: KotlinProjectStructureMetadata,
        moduleMetadata: Module,
        level: ResolutionLevel
    ) = if (hasJarEntry(kmpMetadataFile.getPath()!!, sourceSetName) == true) {
        kmpMetadataFile.getPath()!!
    } else {
        val contextIosPlatforms = allIosPlatforms.intersect(context.settings.platforms)
        if (contextIosPlatforms.isNotEmpty()) {
            // 1. Find names of all variants that declare this sourceSet
            val variantsWithSourceSet = kotlinProjectStructureMetadata.projectStructure.variants
                .filter { it.sourceSet.contains(sourceSetName) }
                .map { it.name }

            // 2. Find iOS variants for actual iOS platforms
            val iosVariants = contextIosPlatforms.flatMap { platform ->
                resolveVariants(moduleMetadata, context.settings, platform)
                    .withoutDocumentationAndMetadata
                    .map { it to platform }
            }

            iosVariants.firstOrNull {
                // 3. Filter the first iOS variant that declares sourceSet
                it.first.name.removeSuffix("-published") in variantsWithSourceSet
            }?.let {
                val platform = it.second
                // 4. Try to download artifact from that variant and resolve dependency from that
                it.first
                    .`available-at`
                    ?.asDependency()
                    ?.toMavenDependency(context, moduleMetadata)
                    ?.let {
                        val depModuleMetadata = it.parseModuleMetadata(context, level)
                        it.detectKotlinMetadataLibrary(context, platform, depModuleMetadata, level)
                    }?.let {
                        it.second.getPath()?.takeIf { hasJarEntry(it, sourceSetName) == true }
                    }
            }
        } else null
    }

    private fun resolveVariants(
        module: Module,
        settings: Settings,
        platform: ResolutionPlatform
    ): List<Variant> {
        val initiallyFilteredVariants = module
            .variants
            // todo (AB) : Why filtering against capabilities?
            .filter { it.capabilities.isEmpty() || it.capabilities == listOf(toCapability()) || it.isOneOfExceptions() }
            .filter { nativeTargetMatches(it, platform) }

        val validVariants = initiallyFilteredVariants
            .filterWithFallbackPlatform(platform)
            .filterWithFallbackScope(settings.scope)
            .filterMultipleVariantsByUnusedAttributes()

        return validVariants
    }

    private fun reportDependencyVersionResolutionFailure(dependency: Dependency, module: Module) {
        messages.asMutable() += Message(
            "Module ${module.component.group}:${module.component.module}:${module.component.version} " +
                    "depends on ${dependency.group}:${dependency.module}, but version of the dependency could not be resolved: " +
                    "neither 'requires' nor 'prefers' nor 'strictly' attributes are defined",
            severity = Severity.ERROR
        )
    }

    private fun Variant.isOneOfExceptions() = isKotlinException() || isGuavaException()

    private fun Variant.isKotlinException() =
        isKotlinTestJunit() && capabilities.sortedBy { it.name } == listOf(
            Capability(group, "kotlin-test-framework-impl", version),
            toCapability()
        )

    // Skip metadata un-packaging for kotlin-test annotations.
    private fun isKotlinTestAnnotations() =
        group == "org.jetbrains.kotlin" && (module in setOf("kotlin-test-annotations-common"))

    private fun isKotlinTestJunit() =
        group == "org.jetbrains.kotlin" && (module in setOf("kotlin-test-junit", "kotlin-test-junit5"))

    private fun isKotlinTestAnnotationsCommon() =
        group == "org.jetbrains.kotlin" && module == "kotlin-test-annotations-common"

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

    private fun nativeTargetMatches(variant: Variant, platform: ResolutionPlatform) =
        variant.attributes["org.jetbrains.kotlin.platform.type"] != PlatformType.NATIVE.value
                || variant.attributes["org.jetbrains.kotlin.native.target"] == null
                || variant.attributes["org.jetbrains.kotlin.native.target"] == platform.nativeTarget

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

        (project.dependencyManagement?.dependencies?.dependencies ?: listOf()).filter {
            it.version != null && it.optional != true
        }.map {
            context.createOrReuseDependencyConstraint(it.groupId, it.artifactId, Version(requires = it.version!!))
        }.let {
            dependencyConstraints = it
        }

        (project.dependencies?.dependencies ?: listOf()).filter {
            context.settings.scope.matches(it)
        }.filter {
            it.version != null && it.optional != true
        }.map {
            context.createOrReuseDependency(it.groupId, it.artifactId, it.version!!)
        }.let {
            children = it
        }

        packaging = project.packaging ?: "jar"
        state = level.state
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
            context.createOrReuseDependency(it.groupId, it.artifactId, it.version)
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
                    val dependency = context.createOrReuseDependency(it.groupId, it.artifactId, it.version)
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
            ?.map { dep ->
                if (dep.version != null && dep.scope != null) return@map dep
                dependencyManagement
                    ?.find { it.groupId == dep.groupId && it.artifactId == dep.artifactId }
                    ?.let { dependencyManagementEntry ->
                        return@map dep
                            .let {
                                if (dep.version == null && dependencyManagementEntry.version != null) it.copy(version = dependencyManagementEntry.version)
                                else it
                            }.let {
                                if (dep.scope == null && dependencyManagementEntry.scope != null) it.copy(scope = dependencyManagementEntry.scope)
                                else it
                            }
                    }
                    ?: return@map dep
            }
            ?.map { it.expandTemplates(project) }
        return project.copy(
            dependencies = dependencies?.let { Dependencies(it) },
            dependencyManagement = dependencyManagement?.let { DependencyManagement(Dependencies(it)) },
        )
    }

    private suspend fun DependencyFile.isDownloadedOrDownload(level: ResolutionLevel, context: Context) =
        isDownloaded() && hasMatchingChecksum(level, context) || level == ResolutionLevel.NETWORK && download(context)

    private val Collection<Variant>.withoutDocumentationAndMetadata: List<Variant>
        get() = filterNot { it.isDocumentationOrMetadata }

    private val Variant.isDocumentationOrMetadata: Boolean
        get() =
            attributes["org.gradle.category"] == "documentation"
                    ||
                    attributes["org.gradle.usage"] == "kotlin-api"
                    && attributes["org.jetbrains.kotlin.platform.type"] == PlatformType.COMMON.value

    suspend fun downloadDependencies(context: Context, downloadSources: Boolean = false) {
        val notDownloaded = files(downloadSources)
            .filter {
                context.settings.platforms.size == 1 // Verification of multiplatform hash is done at the file-producing stage
                        && !(it.isDownloaded() && it.hasMatchingChecksum(ResolutionLevel.NETWORK, context))
            }

        notDownloaded.forEach { it.download(context) }
    }
}

internal fun <E> List<E>.asMutable(): MutableList<E> = this as MutableList<E>

private fun Dependency.isBom(): Boolean = attributes["org.gradle.category"] == "platform"

private val allIosPlatforms = ResolutionPlatform.entries.filter { it.name.startsWith("IOS_") }

// todo (AB) : 'strictly' should have special support (we have to take this into account during conflict resolution)
internal fun Version.resolve() = strictly?.takeIf { !it.isInterval() }
    ?: requires?.takeIf { !it.isInterval() }
    ?: prefers?.takeIf { !it.isInterval() }

private fun String.isInterval() = startsWith("[") || startsWith("]")

// todo (AB) :
// - interval are not implemented and we need to warn/show error to user about it
// - now nstrictly is treated as requires, should be supported properly
