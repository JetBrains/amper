/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.jetbrains.amper.concurrency.computeHash
import org.jetbrains.amper.concurrency.produceFileWithDoubleLockAndHash
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.BomVariantNotFound
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.FailedRepackagingKMPLibrary
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.KotlinMetadataHashNotResolved
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.KotlinMetadataMissing
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.KotlinMetadataNotResolved
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.KotlinProjectStructureMetadataMissing
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.ModuleFileNotDownloaded
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.MoreThanOneVariant
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.MoreThanOneVariantWithoutMetadata
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.NoVariantForPlatform
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.PomWasFoundButMetadataIsMissing
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.PomWasNotFound
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.ProjectHasMoreThanTenAncestors
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.UnableToDetermineDependencyVersion
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.UnableToDetermineDependencyVersionForKotlinLibrary
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.UnableToParseMetadata
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.UnableToParsePom
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.UnableToResolveDependency
import org.jetbrains.amper.dependency.resolution.DependencyResolutionDiagnostics.UnexpectedDependencyFormat
import org.jetbrains.amper.dependency.resolution.LocalM2RepositoryFinder.findPath
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
import org.jetbrains.amper.dependency.resolution.metadata.xml.localRepository
import org.jetbrains.amper.dependency.resolution.metadata.xml.parsePom
import org.jetbrains.amper.dependency.resolution.metadata.xml.parseSettings
import org.jetbrains.amper.dependency.resolution.metadata.xml.plus
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private val logger = LoggerFactory.getLogger("maven.kt")

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
        version: String?,
        isBom: Boolean,
        parentNodes: List<DependencyNode> = emptyList(),
    ) : this(
        templateContext,
        templateContext.createOrReuseDependency(group, module, version, isBom),
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
    val version: String? = dependency.version
    val isBom: Boolean = dependency.isBom

    var overriddenBy: List<DependencyNode> = emptyList()
        internal set

    override val context: Context = templateContext.copyWithNewNodeCache(parentNodes)
    override val key: Key<MavenDependency> = Key<MavenDependency>("$group:$module")
    override val children: List<DependencyNode> by PropertyWithDependencyGeneric(
        dependencyProviders = listOf(
            { thisRef: MavenDependencyNode -> thisRef.dependency.children },
            { thisRef: MavenDependencyNode -> thisRef.dependency.dependencyConstraints }
        ),
        valueProvider = { dependencies ->
            val children = dependencies[0] as List<*>
            val dependencyConstraints = dependencies[1] as List<*>
            children.map { it as MavenDependency }.mapNotNull {
                context
                    .getOrCreateNode(it, this)
                    // skip children that form cyclic dependencies
                    .takeIf { !it.isDescendantOf(it) }
            } + dependencyConstraints.map {
                context.getOrCreateConstraintNode(it as MavenDependencyConstraint, this)
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

    override suspend fun downloadDependencies(downloadSources: Boolean) =
        dependency.downloadDependencies(context, downloadSources)

    override fun toString(): String = if (dependency.version == version) {
        dependency.toString()
    } else {
        "$group:$module:${version.orUnspecified()} -> ${dependency.version}"
    }

    private fun DependencyNode.isDescendantOf(parent: DependencyNode): Boolean {
        return parents.any { it.key == parent.key }
                || parents.any { it.isDescendantOf(parent) }
    }

    fun getOriginalMavenCoordinates(): MavenCoordinates = dependency.coordinates.copy(version = version)

    /**
     * Publishing of a KMP library involves publishing various variants of this library for different platforms.
     * JVM single-platform variant might be consumed by a simple Maven/Gradle project that is not aware of KMP at all.
     * That means, JVM variant, when it comes to declaring dependencies during publication, should not refer to other KMP libraries,
     * but instead it should declare dependencies on JVM-specific variants of those libraries.
     *
     * This method provides coordinates of platform-specific variant for the KMP library.
     *
     * Method is supposed to be called on a maven dependency node from the resolved dependencies graph.
     * If the dependency is a KMP library, but resolution is done in a single-platform context,
     * then this method returns coordinates of the platform-specific library variant.
     * Otherwise (non KMP dependency or multi-platform resolution context), the original dependency coordinates are returned.
     *
     * Returned coordinates should be used while declaring dependency of the platform-specific variant being published
     * instead of the reference on the KMP library itself.
     *
     * Note: Returned maven coordinates contain an original dependency version, which is declared in the configuration file,
     * not the actual one that might have been changed due to a conflict resolution process.
     * This is because declared dependencies are published, not resolved ones.
     */
    fun getMavenCoordinatesForPublishing(): MavenCoordinates {
        if (context.settings.platforms.size == 1) {
            if (dependency.files(false).isEmpty()) { // todo (AB) : Replace with isKmpLibrary() condition
                val childrenOriginalCoordinates = children
                    .filterIsInstance<MavenDependencyNode>()
                    .mapNotNull { nested ->
                        nested.originalVersion()?.let { nested.dependency.coordinates.copy(version = it) }
                    }
                val singlePlatformCoordinates = with(dependency) { variants.withoutDocumentationAndMetadata }
                    .mapNotNull { it.`available-at`?.toCoordinates() }
                    .singleOrNull { it in childrenOriginalCoordinates }

                if (singlePlatformCoordinates != null) return singlePlatformCoordinates
            }
        }

        return getOriginalMavenCoordinates()
    }
}

class UnresolvedMavenDependencyNode(
    val coordinates: String,
    templateContext: Context,
    parentNodes: List<DependencyNode> = emptyList(),
    reasons: List<String>,
) : DependencyNode {
    init {
        require(reasons.isNotEmpty()) { "Reasons for creating an unresolved node must not be empty." }
    }

    override val context = templateContext.copyWithNewNodeCache(parentNodes)
    override val key: Key<*> = Key<UnresolvedMavenDependencyNode>(coordinates)
    override val children: List<DependencyNode> = emptyList()
    override val messages: List<Message> = reasons.map { Message(it, severity = Severity.ERROR) }
    override suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean) {}
    override suspend fun downloadDependencies(downloadSources: Boolean) {}
    override fun toString(): String = "$coordinates, unresolved"
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

    var overriddenBy: List<DependencyNode> = emptyList()
        internal set

    override val context: Context = templateContext.copyWithNewNodeCache(parentNodes)
    override val key: Key<*> = Key<MavenDependency>("$group:$module") // reusing the same key as MavenDependencyNode
    override val children: List<DependencyNode> = emptyList()
    override val messages: List<Message> = emptyList()

    override suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean) {}

    override suspend fun downloadDependencies(downloadSources: Boolean) {}

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
private class PropertyWithDependencyGeneric<T, out V : Any>(
    val dependencyProviders: List<DependencyProvider<T>>,
    val valueProvider: (List<Any?>) -> V,
) : ReadOnlyProperty<T, V> {

    @Volatile
    private lateinit var dependencies: List<Any?>

    @Volatile
    private lateinit var value: V

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
    version: String?,
    isBom: Boolean = false
): MavenDependency = this.resolutionCache.computeIfAbsent(Key<MavenDependency>("$group:$module:${version.orUnspecified()}:$isBom")) {
    MavenDependency(this.settings, group, module, version, isBom)
}

internal fun Context.createOrReuseDependencyConstraint(
    group: String,
    module: String,
    version: Version
): MavenDependencyConstraint =
    this.resolutionCache.computeIfAbsent(Key<MavenDependencyConstraint>("$group:$module:$version")) {
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
    val coordinates: MavenCoordinates,
    val isBom: Boolean
) {
    internal constructor(
        settings: Settings,
        groupId: String,
        artifactId: String,
        version: String?,
        isBom: Boolean = false,
    ) : this(settings, MavenCoordinates(groupId, artifactId, version), isBom)

    val group: String = coordinates.groupId
    val module: String = coordinates.artifactId
    val version: String? = coordinates.version

    @Volatile
    var state: ResolutionState = ResolutionState.INITIAL
        private set

    @Volatile
    internal var variants: List<Variant> = listOf()

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

    internal var metadataResolutionFailureMessage: Message? = null

    internal val messages: List<Message>
        get() = if (version == null) {
            listOf(
                Message(
                    "Version of dependency is not specified, it has not been resolved by dependency resolution",
                    severity = Severity.ERROR
                )
            )
        } else {
            metadataResolutionFailureMessage
                ?.let { listOf(it) }
                ?: (pom.diagnosticsReporter.getMessages() +
                        moduleFile.diagnosticsReporter.getMessages() +
                        files(withSources = true).flatMap { it.diagnosticsReporter.getMessages() })
        }

    private val mutex = Mutex()

    internal val moduleFile = getDependencyFile(this, getNameWithoutExtension(this), "module")
    private var moduleMetadata: Module? = null
    val pom = getDependencyFile(this, getNameWithoutExtension(this), "pom")
    private var pomText: String? = null

    fun files(withSources: Boolean = false) =
        if (withSources)
            _files
        else
            _filesWithoutSources.filterNot { it.hasSourcesFilename() }

    private fun DependencyFile.hasSourcesFilename(): Boolean =
        fileName.endsWith("-sources.jar") || fileName.endsWith("-javadoc.jar")

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
                val variants = dependencies[0] as List<*>
                val packaging = dependencies[1] as String?
                val sourceSetsFiles = dependencies[2] as List<*>
                buildList {
                    variants
                        .map { it as Variant }
                        .let { if (withSources) it else it.withoutDocumentationAndMetadata }
                        .let {
                            val areSourcesMissing = withSources && it.documentationOnly.isEmpty()
                            it
                                .flatMap { it.files }
                                .forEach {
                                    add(getDependencyFile(this@MavenDependency, it))
                                    if (areSourcesMissing) {
                                        add(getAutoAddedSourcesDependencyFile())
                                    }
                                }
                        }
                    packaging?.takeIf { it != "pom" }?.let {
                        val nameWithoutExtension = getNameWithoutExtension(this@MavenDependency)
                        val extension = if (it == "bundle" || it.endsWith("-plugin")) "jar" else it
                        add(getDependencyFile(this@MavenDependency, nameWithoutExtension, extension))
                        if (extension == "jar" && withSources) {
                            add(getAutoAddedSourcesDependencyFile())
                        }
                    }
                    addAll(sourceSetsFiles.map { it as DependencyFile })
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

    override fun toString(): String = "$group:$module:${version.orUnspecified()}"

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
        try {
            if (version == null)
                return

            resetMetadataDiagnostics()

            val settings = context.settings
            // 1. Download pom.
            val pomText = if (pom.isDownloadedOrDownload(level, context, pom.diagnosticsReporter)) {
                pom.readText()
            } else {
                if (level != ResolutionLevel.NETWORK) {
                    pom.diagnosticsReporter.addMessage(
                        PomWasNotFound.asMessage(
                            this,
                            extra = DependencyResolutionBundle.message(
                                "extra.repositories",
                                settings.repositories.joinToString()
                            ),
                        )
                    )
                }
                null
            }
            this.pomText = pomText

            // 2. If pom is missing or mentions metadata, use it.
            if (pomText == null || pomText.contains("do_not_remove: published-with-gradle-metadata")) {
                if (moduleFile.isDownloadedOrDownload(level, context, moduleFile.diagnosticsReporter)) {
                    resolveUsingMetadata(context, level, moduleFile.diagnosticsReporter)
                    return
                }
                if (pomText != null) {
                    if (level != ResolutionLevel.NETWORK) {
                        moduleFile.diagnosticsReporter.addMessage(
                            PomWasFoundButMetadataIsMissing.asMessage(
                                this,
                                extra = DependencyResolutionBundle.message(
                                    "extra.repositories",
                                    settings.repositories.joinToString()
                                ),
                            )
                        )
                    }
                }
            }
            // 3. If metadata can't be used, use pom.
            if (pomText != null) {
                resolveUsingPom(pomText, context, level, pom.diagnosticsReporter)
                return
            }
        } finally {
            // 4. if neither pom nor module file were downloaded
            if (state < level.state) {
                metadataResolutionFailureMessage = UnableToResolveDependency.asMessage(
                    this,
                    extra = DependencyResolutionBundle.message(
                        "extra.repositories",
                        context.settings.repositories.joinToString()
                    ),
                    overrideSeverity = Severity.WARNING.takeIf { level != ResolutionLevel.NETWORK },
                    suppressedMessages = pom.diagnosticsReporter.getMessages() + moduleFile.diagnosticsReporter.getMessages()
                )
            }
        }
    }

    private fun resetMetadataDiagnostics() {
        metadataResolutionFailureMessage = null
        pom.diagnosticsReporter.reset()
        moduleFile.diagnosticsReporter.reset()
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

    private fun List<Variant>.filterWithFallbackJvmEnvironment(platform: ResolutionPlatform): List<Variant> {
        val platformVariants = this.filter { platform.type.matchesJvmEnvironment(it) }
        return when {
            platformVariants.withoutDocumentationAndMetadata.isNotEmpty()
                    || platform.type.fallback == null -> platformVariants

            else -> this.filter { platform.type.fallback.matchesJvmEnvironment(it) }
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
                    "org.gradle.jvm.environment"
                )
                val minUnusedAttrsCount = this.minOfOrNull { v ->
                    v.attributes.count { it.key !in usedAttributes }
                }
                this.filter { v -> v.attributes.count { it.key !in usedAttributes } == minUnusedAttrsCount }
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

    private suspend fun resolveUsingMetadata(
        context: Context,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ) {
        val moduleMetadata = parseModuleMetadata(context, level, diagnosticsReporter)
        this.moduleMetadata = moduleMetadata

        if (moduleMetadata == null) return

        if (context.settings.platforms.isEmpty()) {
            throw AmperDependencyResolutionException("Target platform is not specified.")
        } else if (context.settings.platforms.singleOrNull() == ResolutionPlatform.COMMON) {
            throw AmperDependencyResolutionException(
                "Dependency resolution can not be run for COMMON platform. " +
                        "Set of actual target platforms should be specified."
            )
        }

        val processedAsSpecialCase = processSpecialKmpLibraries(context, moduleMetadata, level, diagnosticsReporter)
        if (processedAsSpecialCase) {
            // Do nothing, dependency was already processed
        } else if (isBom) {
            val validVariants = resolveBomVariants(moduleMetadata, context.settings)
            if (validVariants.isEmpty()) {
                diagnosticsReporter.addMessage(BomVariantNotFound.asMessage(this))
            } else {
                validVariants.also {
                    variants = it
                    if (it.withoutDocumentationAndMetadata.size > 1) {
                        diagnosticsReporter.addMessage(
                            MoreThanOneVariant.asMessage(
                                this,
                                extra = it.withoutDocumentationAndMetadata.joinToString { variant -> variant.name },
                            )
                        )
                    }
                }
                // Import platform/BOM dependency constraints
                validVariants
                    .withoutDocumentationAndMetadata
                    .let { variants ->
                        variants.flatMap {
                            it.dependencyConstraints
                        }.mapNotNull {
                            it.toMavenDependencyConstraint(context)
                        }.let {
                            dependencyConstraints = it
                        }
                    }
            }
        } else {
            // Regular library
            if (context.settings.platforms.size == 1) {
                val platform = context.settings.platforms.single()
                val validVariants = resolveVariants(moduleMetadata, context.settings, platform)

                if (validVariants.isEmpty()) {
                    diagnosticsReporter.addMessage(
                        // todo (AB) : Describe what variants we have and why they doesn't match, see example of the explanation in
                        // todo (AB) : https://youtrack.jetbrains.com/issue/AMPER-3842/#focus=Comments-27-11433793.0-0
                        NoVariantForPlatform.asMessage(platform.pretty, this)
                    )
                } else {
                    validVariants.also {
                        variants = it
                        if (it.withoutDocumentationAndMetadata.size > 1) {
                            diagnosticsReporter.addMessage(
                                MoreThanOneVariant.asMessage(
                                    this,
                                    extra = it.withoutDocumentationAndMetadata.joinToString { it.name },
                                )
                            )
                        }
                    }
                    // One platform case
                    validVariants
                        .withoutDocumentationAndMetadata
                        .let { variants ->
                            variants.flatMap {
                                it.dependencies(
                                    context,
                                    moduleMetadata,
                                    level,
                                    diagnosticsReporter
                                ) + listOfNotNull(it.`available-at`?.asDependency())
                            }.mapNotNull {
                                it.toMavenDependency(context, moduleMetadata, diagnosticsReporter)
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
                }
            } else {
                // Multiplatform case
                val (kotlinMetadataVariant, kmpMetadataFile) =
                    detectKotlinMetadataLibrary(
                        context,
                        ResolutionPlatform.COMMON,
                        moduleMetadata,
                        level,
                        diagnosticsReporter
                    )
                        ?: return  // the children list is empty in case kmp common variant is not resolved

                resolveKmpLibrary(
                    kmpMetadataFile,
                    context,
                    moduleMetadata,
                    level,
                    kotlinMetadataVariant,
                    diagnosticsReporter
                )

                // Add KMP sources
                val sourcesDependencyFile =
                    getKotlinMetadataSourcesVariant(moduleMetadata.variants, ResolutionPlatform.COMMON)
                        ?.let { getKotlinMetadataFile(it, diagnosticsReporter) }
                        ?.let { getDependencyFile(this, it) }
                        ?: getAutoAddedSourcesDependencyFile()

                sourceSetsFiles = sourceSetsFiles.toMutableList() + listOf(sourcesDependencyFile)
            }
        }

        state = level.state

        // We have used a module metadata file for resolving dependency, => lower severity of pom-related issues.
        pom.diagnosticsReporter.lowerSeverityTo(Severity.WARNING)
    }

    fun getAutoAddedSourcesDependencyFile() =
        getDependencyFile(
            this,
            "${this.moduleFile.nameWithoutExtension}-sources",
            "jar",
            isAutoAddedDocumentation = true
        )

    /**
     * Some pretty basic libraries that are used in KMP world mimic for a pure JVM libraries,
     * those cases should be processed in a custom way.
     *
     * If this method returns true, it means such a library was detected and processed (library dependencies are registered to graph),
     * no further processing by a usual way is needed.
     *
     * @return true, in case Kmp library that needs special treatment was detected and processed, false - otherwise
     */
    private suspend fun processSpecialKmpLibraries(
        context: Context,
        moduleMetadata: Module,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ): Boolean {
        if (isKotlinTestAnnotationsCommon()
            || isKotlinStdlibCommon()
        ) {
            moduleMetadata
                .variants
                .let {
                    it.flatMap {
                        it.dependencies(context, moduleMetadata, level, diagnosticsReporter)
                    }.mapNotNull {
                        it.toMavenDependency(context, moduleMetadata, diagnosticsReporter)
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

    private fun Dependency.toMavenDependency(
        context: Context,
        reportError: (reason: String) -> Unit = {},
    ): MavenDependency? {
        val resolvedVersion = resolveVersion(reportError)
        return resolvedVersion?.let { context.createOrReuseDependency(group, module, resolvedVersion, isBom()) }
    }

    private fun Dependency.toMavenDependency(
        context: Context,
        module: Module,
        diagnosticsReporter: DiagnosticReporter
    ): MavenDependency? {
        val dependency = this
        return toMavenDependency(context) { reason ->
            val coordinates = "${module.component.group}:${module.component.module}:${module.component.version}"
            val dependency = "${dependency.group}:${dependency.module}"
            diagnosticsReporter.addMessage(
                UnableToDetermineDependencyVersion.asMessage(
                    coordinates,
                    dependency,
                    reason,
                )
            )
        }
    }

    private suspend fun Variant.bomDependencyConstraints(
        context: Context,
        module: Module,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ): List<MavenDependencyConstraint> =
        dependencies.mapNotNull {
            if (!it.isBom()) {
                null
            } else {
                withResolvedVersion(it)
                    .takeIf { it.version != null }
                    ?.toMavenDependency(context, module, diagnosticsReporter)
                    ?.let {
                        it.resolveChildren(context, level)
                        it.dependencyConstraints
                    }
            }
        }.flatten().takeIf { it.isNotEmpty() }
        // try to resolve constraints from '.pom' - descriptor
            ?: pomText?.let { resolveDependenciesConstraintsUsingPom(it, context, level, diagnosticsReporter) }
            ?: emptyList()

    private suspend fun Variant.dependencies(
        context: Context,
        module: Module,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ): List<Dependency> =
        dependencies.map {
            withResolvedVersion(it) { bomDependencyConstraints(context, module, level, diagnosticsReporter) }
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
    private suspend fun Variant.withResolvedVersion(
        dep: Dependency,
        bomDependencyConstraints: suspend () -> List<MavenDependencyConstraint> = { emptyList() }
    ): Dependency =
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

    private fun Dependency.resolveVersion(reportError: (reason: String) -> Unit): String? {
        return if (version == null) {
            reportError(DependencyResolutionBundle.message("attribute.version.is.not.defined"))
            null
        } else {
            val resolvedVersion = version.resolve()
            if (resolvedVersion == null) {
                reportError(DependencyResolutionBundle.message("version.attributes.not.defined"))
                return null
            }
            resolvedVersion
        }
    }

    private suspend fun parseModuleMetadata(
        context: Context,
        level: ResolutionLevel,
        diagnosticReporter: DiagnosticReporter
    ): Module? {
        if (moduleFile.isDownloadedOrDownload(level, context, diagnosticReporter)) {
            try {
                return moduleFile.readText().parseMetadata()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                diagnosticReporter.addMessage(
                    UnableToParseMetadata.asMessage(
                        moduleFile,
                        extra = DependencyResolutionBundle.message("extra.exception", e),
                        exception = e,
                    )
                )
            }
        } else {
            diagnosticReporter.addMessage(
                ModuleFileNotDownloaded.asMessage(
                    this,
                    extra = DependencyResolutionBundle.message(
                        "extra.repositories",
                        context.settings.repositories.joinToString()
                    ),
                    overrideSeverity = Severity.WARNING.takeIf { level != ResolutionLevel.NETWORK },
                )
            )
        }
        return null
    }

    private suspend fun detectKotlinMetadataLibrary(
        context: Context,
        platform: ResolutionPlatform,
        moduleMetadata: Module?,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ): Pair<Variant, DependencyFile>? {
        val resolvedModuleMetadata = moduleMetadata
            ?: parseModuleMetadata(context, level, diagnosticsReporter)
            ?: return null

        val kotlinMetadataVariant =
            getKotlinMetadataVariant(resolvedModuleMetadata.variants, platform, diagnosticsReporter)
                ?: return null  // children list is empty in case kmp common variant is not resolved
        val kotlinMetadataFile = getKotlinMetadataFile(kotlinMetadataVariant, diagnosticsReporter)
            ?: return null  // children list is empty if kmp common variant file is not resolved

        val kmpMetadataDependencyFile = getDependencyFile(this, kotlinMetadataFile)

        return (kotlinMetadataVariant to kmpMetadataDependencyFile).takeIf {
            it.second.isDownloadedOrDownload(
                level,
                context,
                it.second.diagnosticsReporter
            )
        }
            ?: run {
                diagnosticsReporter.addMessage(
                    KotlinMetadataMissing.asMessage(
                        kmpMetadataDependencyFile.fileName,
                        this,
                        overrideSeverity = Severity.WARNING.takeIf { level != ResolutionLevel.NETWORK },
                        suppressedMessages = kmpMetadataDependencyFile.diagnosticsReporter.getMessages(),
                    )
                )
                null
            }
    }

    private suspend fun resolveKmpLibrary(
        kmpMetadataFile: DependencyFile,
        context: Context,
        moduleMetadata: Module,
        level: ResolutionLevel,
        kotlinMetadataVariant: Variant,
        diagnosticsReporter: DiagnosticReporter
    ) {
        val kmpMetadata = kmpMetadataFile.getPath()?.let {
            readJarEntry(it, "META-INF/kotlin-project-structure-metadata.json")
        } ?: run {
            diagnosticsReporter.addMessage(KotlinProjectStructureMetadataMissing.asMessage(kmpMetadataFile.fileName))
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
                            level,
                            diagnosticsReporter
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
                    diagnosticsReporter.addMessage(UnexpectedDependencyFormat.asMessage(this, rawDep))
                    null
                } else {
                    val dependencyGroup = parts[0]
                    val dependencyModule = parts[1]
                    kotlinMetadataVariant.dependencies(context, moduleMetadata, level, diagnosticsReporter)
                        .firstOrNull { it.group == dependencyGroup && it.module == dependencyModule }
                        ?.toMavenDependency(context) { reason ->
                            diagnosticsReporter.addMessage(
                                UnableToDetermineDependencyVersionForKotlinLibrary.asMessage(
                                    this,
                                    rawDep,
                                    reason,
                                )
                            )
                        }
                }
            }

        // todo (AB) : take kotlinMetadataVariant.dependencyConstraints into account as well? What subset on constraints should be taken into account?
    }

    private fun getKotlinMetadataFile(kotlinMetadataVariant: Variant, diagnosticReporter: DiagnosticReporter) =
        kotlinMetadataVariant.files.singleOrNull()
            ?: run {
                diagnosticReporter.addMessage(KotlinMetadataNotResolved.asMessage(this))
                null
            }

    private fun getKotlinMetadataVariant(
        validVariants: List<Variant>,
        platform: ResolutionPlatform,
        diagnosticReporter: DiagnosticReporter
    ): Variant? {
        if (this.isKotlinTestAnnotationsCommon()) return null
        val metadataVariants = validVariants.filter { it.isKotlinMetadata(platform) }
        return metadataVariants.firstOrNull() ?: run {
            diagnosticReporter.addMessage(MoreThanOneVariantWithoutMetadata.asMessage(this))
            null
        }
    }

    private fun getKotlinMetadataSourcesVariant(
        validVariants: List<Variant>,
        platform: ResolutionPlatform
    ): Variant? {
        if (this.isKotlinTestAnnotationsCommon()) return null
        return validVariants.firstOrNull { it.isKotlinMetadataSources(platform) }
    }

    private suspend fun SourceSet.toDependencyFile(
        kmpMetadataFile: DependencyFile,
        moduleMetadata: Module,
        kotlinProjectStructureMetadata: KotlinProjectStructureMetadata? = null, // is empty for sources only
        context: Context,
        level: ResolutionLevel,
        diagnosticReporter: DiagnosticReporter
    ): DependencyFile? =
        toDependencyFile(
            name,
            kmpMetadataFile,
            moduleMetadata,
            kotlinProjectStructureMetadata,
            context,
            level,
            diagnosticReporter
        )

    // todo (AB) : All this logic might be wrapped into ExecuteOnChange in order to skip metadata resolution in case targetFile exists already
    private suspend fun toDependencyFile(
        sourceSetName: String,
        kmpMetadataFile: DependencyFile,
        moduleMetadata: Module,
        kotlinProjectStructureMetadata: KotlinProjectStructureMetadata? = null, // is empty for sources only
        context: Context,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ): DependencyFile? {
        val group = kmpMetadataFile.dependency.group
        val module = kmpMetadataFile.dependency.module
        val version = kmpMetadataFile.dependency.version
        // kmpMetadataFile hash
        val sha1 = kmpMetadataFile.getExpectedHash("sha1")
            ?: kmpMetadataFile.getPath()?.let {
                computeHash(it, "sha1").hash
            }
            ?: run {
                diagnosticsReporter.addMessage(
                    KotlinMetadataHashNotResolved.asMessage(
                        kmpMetadataFile.dependency,
                        overrideSeverity = Severity.INFO.takeIf { level != ResolutionLevel.NETWORK },
                    )
                )
                return null
            }

        val kmpLibraryWithSourceSet = resolveKmpLibraryWithSourceSet(
            sourceSetName,
            kmpMetadataFile,
            context,
            kotlinProjectStructureMetadata,
            moduleMetadata,
            level,
            diagnosticsReporter
        ) ?: run {
            logger.debug("Source set $sourceSetName for Kotlin Multiplatform library ${kmpMetadataFile.fileName} is not found")
            return null
        }

        val extension = if (kmpMetadataFile.hasSourcesFilename()) "jar" else "klib"
        val sourcesSuffix = if (kmpMetadataFile.hasSourcesFilename()) "-sources" else ""

        val sourceSetFile = DependencyFile(
            kmpMetadataFile.dependency,
            "${kmpMetadataFile.dependency.module}-$sourceSetName$sourcesSuffix",
            extension,
            kmpSourceSet = sourceSetName
        )

        val targetFileName = "$module-$sourceSetName-$version$sourcesSuffix.$extension"

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
                diagnosticsReporter.addMessage(
                    FailedRepackagingKMPLibrary.asMessage(
                        kmpLibraryWithSourceSet.name,
                        extra = DependencyResolutionBundle.message("extra.exception", e),
                        exception = e,
                    )
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
        kotlinProjectStructureMetadata: KotlinProjectStructureMetadata?,
        moduleMetadata: Module,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ) = if (hasJarEntry(kmpMetadataFile.getPath()!!, sourceSetName) == true) {
        kmpMetadataFile.getPath()!!
    } else if (kotlinProjectStructureMetadata == null) {
        null
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
                    ?.toMavenDependency(context, moduleMetadata, diagnosticsReporter)
                    ?.let {
                        val depModuleMetadata = it.parseModuleMetadata(context, level, diagnosticsReporter)
                        it.detectKotlinMetadataLibrary(context, platform, depModuleMetadata, level, diagnosticsReporter)
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
            .filter { capabilityMatches(it) }
            .filter { nativeTargetMatches(it, platform) }
            .filter { categoryMatches(it) }

        val validVariants = initiallyFilteredVariants
            .filterWithFallbackPlatform(platform)
            .filterWithFallbackJvmEnvironment(platform)
            .filterWithFallbackScope(settings.scope)
            .filterMultipleVariantsByUnusedAttributes()

        return validVariants
    }

    private fun resolveBomVariants(
        module: Module,
        settings: Settings
    ): List<Variant> = module.variants
        .filter { categoryMatches(it) }
        .filterWithFallbackScope(settings.scope)

    private fun Variant.isOneOfExceptions() = isKotlinException() || isGuavaException()

    private fun Variant.isKotlinException() =
        isKotlinTestJunit() && capabilities.sortedBy { it.name } == listOf(
            Capability(group, "kotlin-test-framework-impl", version.orUnspecified()),
            toCapability()
        )

    // Skip metadata un-packaging for kotlin-test annotations.
    private fun isKotlinTestJunit() =
        group == "org.jetbrains.kotlin" && (module in setOf("kotlin-test-junit", "kotlin-test-junit5"))

    private fun isKotlinTestAnnotationsCommon() =
        group == "org.jetbrains.kotlin" && module == "kotlin-test-annotations-common"

    private fun isKotlinStdlibCommon() =
        group == "org.jetbrains.kotlin" && module == "kotlin-stdlib-common"

    private fun isKotlinStdlib() =
        group == "org.jetbrains.kotlin" && module == "kotlin-stdlib"

    private fun Variant.isGuavaException() =
        isGuava()
                && capabilities.contains(Capability("com.google.collections", "google-collections", version.orUnspecified()))
                && capabilities.contains(toCapability())
                && attributes["org.gradle.jvm.environment"] == when (version?.substringAfterLast('-')) {
            "android" -> "android"
            "jre" -> "standard-jvm"
            else -> null
        }

    private fun isGuava() = group == "com.google.guava" && module == "guava"

    private fun MavenDependency.toCapability() = Capability(group, module, version.orUnspecified())

    private fun nativeTargetMatches(variant: Variant, platform: ResolutionPlatform) =
        variant.attributes["org.jetbrains.kotlin.platform.type"] != PlatformType.NATIVE.value
                || variant.attributes["org.jetbrains.kotlin.native.target"] == null
                || variant.attributes["org.jetbrains.kotlin.native.target"] == platform.nativeTarget

    private fun categoryMatches(variant: Variant) = variant.isBom() == isBom

    /**
     * Check that either dependency defines no capability, or its capability is equal to the library itself,
     * multiple capabilities are prohibited (apart from several well-known exceptional cases).
     *
     * Allowing libraries with multiple capabilities in a graph would lead to a potential runtime conflict
     * (when libraries with the same capabilities are added to the runtime).
     * Resolution of conflict between libraries with the same capability should be explicitly supported in Amper DR.
     * Until that, such libraries are denied.
     *
     * todo (AB): Why filtering against capabilities, how to make it right? See https://github.com/gradlex-org/jvm-dependency-conflict-resolution
     */
    private fun capabilityMatches(variant: Variant) =
        variant.capabilities.isEmpty() || variant.capabilities == listOf(toCapability()) || variant.isOneOfExceptions()

    private fun AvailableAt.asDependency() = Dependency(group, module, Version(version))

    private suspend fun resolveUsingPom(
        text: String,
        context: Context,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ) {
        val project = resolvePom(text, context, level, diagnosticsReporter) ?: return

        if (isBom) {
            dependencyConstraints = project.resolveDependenciesConstraints(context)
        } else {
            (project.dependencies?.dependencies ?: listOf()).filter {
                context.settings.scope.matches(it)
            }.filter {
                it.version != null && it.optional != true
            }.map {
                context.createOrReuseDependency(it.groupId, it.artifactId, it.version!!, false)
            }.let {
                children = it
            }
        }

        packaging = project.packaging ?: "jar"
        state = level.state

        // We have used a pom file for resolving dependency, => lower severity of module-metadata-related issues.
        moduleFile.diagnosticsReporter.lowerSeverityTo(Severity.WARNING)
    }

    private suspend fun resolvePom(
        text: String, context: Context, level: ResolutionLevel, diagnosticsReporter: DiagnosticReporter
    ): Project? {
        return try {
            text.parsePom().resolve(context, level, diagnosticsReporter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnosticsReporter.addMessage(
                UnableToParsePom.asMessage(
                    pom,
                    extra = DependencyResolutionBundle.message("extra.exception", e),
                    exception = e,
                )
            )
            return null
        }
    }

    private suspend fun resolveDependenciesConstraintsUsingPom(
        text: String, context: Context, level: ResolutionLevel, diagnosticsReporter: DiagnosticReporter
    ): List<MavenDependencyConstraint> = resolvePom(text, context, level, diagnosticsReporter)
        ?.resolveDependenciesConstraints(context)
        ?: emptyList()

    private fun Project.resolveDependenciesConstraints(context: Context): List<MavenDependencyConstraint> {
        return (dependencyManagement?.dependencies?.dependencies ?: listOf()).filter {
            it.version != null && it.optional != true
        }.map {
            context.createOrReuseDependencyConstraint(it.groupId, it.artifactId, Version(requires = it.version))
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
        diagnosticsReporter: DiagnosticReporter,
        depth: Int = 0,
        origin: Project = this
    ): Project {
        if (depth > 10) {
            diagnosticsReporter.addMessage(ProjectHasMoreThanTenAncestors.asMessage(origin))
            return this
        }
        val parentNode = parent?.let {
            context.createOrReuseDependency(it.groupId, it.artifactId, it.version, isBom = false)
        }

        val project = if (parentNode != null && (parentNode.pom.isDownloadedOrDownload(
                resolutionLevel,
                context,
                diagnosticsReporter
            ))
        ) {
            val text = parentNode.pom.readText()
            val parentProject =
                text.parsePom().resolve(context, resolutionLevel, diagnosticsReporter, depth + 1, origin)
            copy(
                groupId = groupId ?: parentProject.groupId,
                artifactId = artifactId ?: parentProject.artifactId,
                version = version ?: parentProject.version,
                dependencies = dependencies + parentProject.dependencies,
                properties = properties + parentProject.properties,
            ).let {
                val importedDependencyManagement = it.resolveImportedDependencyManagement(
                    context,
                    resolutionLevel,
                    diagnosticsReporter,
                    depth
                )
                it.copy(
                    // Dependencies declared directly in pom.xml dependencyManagement section take precedence over directly imported dependencies,
                    // both in turn take precedence over parent dependencyManagement
                    dependencyManagement = dependencyManagement + importedDependencyManagement + parentProject.dependencyManagement,
                )
            }
        } else if (parent != null && (groupId == null || artifactId == null || version == null)) {
            val importedDependencyManagement = resolveImportedDependencyManagement(
                context,
                resolutionLevel,
                diagnosticsReporter,
                depth
            )
            copy(
                groupId = groupId ?: parent.groupId,
                artifactId = artifactId ?: parent.artifactId,
                version = version ?: parent.version,
                dependencyManagement = dependencyManagement + importedDependencyManagement,
            )
        } else {
            val importedDependencyManagement = resolveImportedDependencyManagement(
                context,
                resolutionLevel,
                diagnosticsReporter,
                depth
            )
            copy(
                dependencyManagement = dependencyManagement + importedDependencyManagement,
            )
        }

        val dependencyManagement = project.dependencyManagement?.copy(
            dependencies = project.dependencyManagement.dependencies?.copy(
                dependencies = project.dependencyManagement.dependencies.dependencies.map { it.expandTemplates(project) }
            )
        )

        val dependencies = project.dependencies
            ?.dependencies
            ?.map { dep ->
                if (dep.version != null && dep.scope != null) {
                    return@map if (dep.version.resolveSingleVersion() != dep.version) {
                        dep.copy(version = dep.version.resolveSingleVersion())
                    } else dep
                }
                dependencyManagement
                    ?.dependencies
                    ?.dependencies
                    ?.find { it.groupId == dep.groupId && it.artifactId == dep.artifactId }
                    ?.let { dependencyManagementEntry ->
                        return@map dep
                            .let {
                                val dependencyManagementEntryVersion =
                                    dependencyManagementEntry.version?.resolveSingleVersion()
                                if (dep.version == null && dependencyManagementEntryVersion != null) it.copy(version = dependencyManagementEntryVersion)
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
            dependencyManagement = dependencyManagement
        )
    }

    /**
     * Resolve an effective imported dependencyManagement.
     * If several dependencies are imported, then those are merged into the only dependencyManagement.
     * The first declared import dependency takes precedence over the second one and so on.
     *
     * Parent poms of imported dependencies are taken into account
     * (in a standard way of resolving dependencyManagement section)
     * Specification tells about import scope:
     *  "It indicates the dependency is to be replaced with the
     *   effective list of dependencies in the specified POM's <dependencyManagement> section."
     *  (https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#dependency-scope)
     */
    private suspend fun Project.resolveImportedDependencyManagement(
        context: Context,
        resolutionLevel: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter,
        depth: Int,
    ): DependencyManagement? = dependencyManagement
        ?.dependencies
        ?.dependencies
        ?.map { it.expandTemplates(this) }
        ?.mapNotNull {
            if (it.scope == "import" && it.version != null) {
                val dependency = context.createOrReuseDependency(it.groupId, it.artifactId, it.version, isBom = true)
                if (dependency.pom.isDownloadedOrDownload(resolutionLevel, context, diagnosticsReporter)) {
                    val text = dependency.pom.readText()
                    val dependencyProject =
                        text.parsePom().resolve(context, resolutionLevel, diagnosticsReporter, depth + 1)
                    dependencyProject.dependencyManagement
                } else {
                    null
                }
            } else {
                null
            }
        }
        ?.takeIf { it.isNotEmpty() }
        ?.reduce(DependencyManagement::plus)

    private suspend fun DependencyFile.isDownloadedOrDownload(
        level: ResolutionLevel,
        context: Context,
        diagnosticsReporter: DiagnosticReporter
    ) =
        isDownloaded() && hasMatchingChecksumLocally(diagnosticsReporter, level)
                || level == ResolutionLevel.NETWORK && download(context, diagnosticsReporter)

    internal val Collection<Variant>.withoutDocumentationAndMetadata: List<Variant>
        get() = filterNot { it.isDocumentationOrMetadata }

    private val Variant.isDocumentationOrMetadata: Boolean
        get() =
            isDocumentation
                    ||
                    attributes["org.gradle.usage"] == "kotlin-api"
                    && attributes["org.jetbrains.kotlin.platform.type"] == PlatformType.COMMON.value

    private val Collection<Variant>.documentationOnly: List<Variant>
        get() = filter { it.isDocumentation }

    private val Variant.isDocumentation: Boolean
        get() = attributes["org.gradle.category"] == "documentation"


    suspend fun downloadDependencies(context: Context, downloadSources: Boolean = false) {
        val withSources = downloadSources || alwaysDownloadSources()

        val allFiles = files(withSources)
        val notDownloaded = allFiles
            .filter {
                (context.settings.platforms.size == 1 // Verification of multiplatform hash is done at the file-producing stage
                        || it.kmpSourceSet == null) // (except for artifact with all sources that is not marked with any kmpSourceSet)
                        && !(it.isDownloaded() && it.hasMatchingChecksumLocally())
            }

        notDownloaded.forEach {
            it.diagnosticsReporter.reset()
            it.download(context, it.diagnosticsReporter)
        }

        allFiles.forEach { it.postProcess(context, withSources, ResolutionLevel.NETWORK, it.diagnosticsReporter) }
    }

    private fun alwaysDownloadSources() = isKotlinStdlib()

    private suspend fun DependencyFile.postProcess(
        context: Context,
        downloadSources: Boolean,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ) {
        repackageKmpLibrarySources(context, downloadSources, level, diagnosticsReporter)
    }

    private suspend fun DependencyFile.repackageKmpLibrarySources(
        context: Context,
        downloadSources: Boolean,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ) {
        if (context.settings.platforms.size > 1
            && downloadSources
            && this.kmpSourceSet == null
            && this.isSourcesDependencyFile()
        ) {
            // repackage KMP library sources.
            val kmpSourcesFile = this
            val sourceSetsSources = coroutineScope {
                sourceSetsFiles
                    .map {
                        async(Dispatchers.IO) {
                            val sourceSetName = it.kmpSourceSet ?: return@async null
                            val moduleMetadata = moduleMetadata ?: error("moduleMetadata wasn't initialized")

                            // Extract sources from this DependencyFile and package extracted sources to separate location
                            toDependencyFile(
                                sourceSetName,
                                kmpSourcesFile,
                                moduleMetadata,
                                null,
                                context,
                                level,
                                diagnosticsReporter
                            )
                        }
                    }
            }.awaitAll()
                .mapNotNull { it }
            // replace all-in-one sources file with per-sourceSet files
            sourceSetsFiles = sourceSetsFiles - this + sourceSetsSources
        }
    }

    private fun DependencyFile.isSourcesDependencyFile(): Boolean =
        !files(withSources = false).map { it.fileName }.contains(this.fileName)
}

/**
 * Describes coordinates of a Maven artifact.
 */
@Serializable
data class MavenCoordinates(
    val groupId: String,
    val artifactId: String,
    // todo (AB) : [AMPER-4112] Support unspecified version of direct dependencies (it could be resolved from BOM later)
    val version: String?,
    val classifier: String? = null
) {
    override fun toString(): String {
        return "$groupId:$artifactId:${version.orUnspecified()}${if (classifier != null) ":$classifier" else ""}"
    }
}

internal fun AvailableAt.toCoordinates() = MavenCoordinates(group, module, version)

private fun Dependency.isBom(): Boolean = attributes["org.gradle.category"] == "platform"
private fun Variant.isBom(): Boolean = attributes["org.gradle.category"] == "platform"

// todo (AB) : This behaviour is applicable perhaps to ALL native platforms
private val allIosPlatforms = ResolutionPlatform.entries.filter { it.name.startsWith("IOS_") }

// todo (AB) : 'strictly' should have special support (we have to take this into account during conflict resolution)
internal fun Version.resolve() = strictly?.resolveSingleVersion()
    ?: requires?.resolveSingleVersion()
    ?: prefers?.resolveSingleVersion()

private fun String.resolveSingleVersion() = removeSquareBracketsForSingleValue().takeIf { !it.isInterval() }
private fun String.isInterval() = startsWith("[") || startsWith("]")
private fun String.removeSquareBracketsForSingleValue() = when {
    startsWith("[") && endsWith("]") && !contains(",") -> substring(1, length - 1)
    else -> this
}

// todo (AB) :
// - interval are not implemented and we need to warn/show error to user about it
// - now strictly is treated as requires, should be supported properly

/**
 * Finds the path to the local .m2 repository as configured in Maven.
 * See the documentation of [findPath] for details.
 */
object LocalM2RepositoryFinder {
    /**
     * Finds the path to the local .m2 repository as configured in Maven.
     *
     * The location for the repository is determined in the same way as Gradle's mavenLocal() publications.
     * It looks at the following locations, in order of precedence:
     *
     * 1. The value of system property 'maven.repo.local' if set;
     * 2. The value of element `<localRepository>` of `~/.m2/settings.xml` if this file exists and element is set;
     * 3. The value of element `<localRepository>` of `$M2_HOME/conf/settings.xml` (where `$M2_HOME` is the value of the
     *    environment variable with that name) if this file exists and element is set;
     * 4. The path `<user.home>/.m2/repository` (where the user home is taken from the system property `user.home`)
     */
    fun findPath(): Path = Path(findLocalM2RepoPathString())

    private fun findLocalM2RepoPathString() = readFromSystemProperty()
        ?: parseFromUserHomeM2Settings()
        ?: parseFromM2HomeConfSettings()
        ?: "${System.getProperty("user.home")}/.m2/repository"

    private fun readFromSystemProperty() = System.getProperty("maven.repo.local")?.takeIf { it.isNotBlank() }

    private fun parseFromUserHomeM2Settings(): String? {
        val userHome = System.getProperty("user.home") ?: return null
        val userHomeSettingsXml = Path("$userHome/.m2/settings.xml")
        return parseLocalRepoIfExists(userHomeSettingsXml)
    }

    private fun parseFromM2HomeConfSettings(): String? {
        val m2Home = System.getenv("M2_HOME")?.takeIf { it.isNotBlank() } ?: return null
        val m2HomeSettingsXml = Path("$m2Home/conf/settings.xml")
        return parseLocalRepoIfExists(m2HomeSettingsXml)
    }

    private fun parseLocalRepoIfExists(settingsXml: Path): String? = resolveSafeOrNull {
        settingsXml
            .takeIf { it.exists() }
            ?.readText()
            ?.parseSettings()
            ?.localRepository()
    }
}

fun String?.orUnspecified() = this ?: "unspecified"
