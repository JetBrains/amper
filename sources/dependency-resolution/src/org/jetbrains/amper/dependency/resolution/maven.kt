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
import kotlinx.serialization.Transient
import org.jetbrains.amper.dependency.resolution.LocalM2RepositoryFinder.findPath
import org.jetbrains.amper.dependency.resolution.attributes.Category
import org.jetbrains.amper.dependency.resolution.attributes.JvmEnvironment
import org.jetbrains.amper.dependency.resolution.attributes.KotlinNativeTarget
import org.jetbrains.amper.dependency.resolution.attributes.KotlinPlatformType
import org.jetbrains.amper.dependency.resolution.attributes.KotlinWasmTarget
import org.jetbrains.amper.dependency.resolution.attributes.PluginApiVersion
import org.jetbrains.amper.dependency.resolution.attributes.Usage
import org.jetbrains.amper.dependency.resolution.attributes.getAttributeValue
import org.jetbrains.amper.dependency.resolution.attributes.hasKotlinNativeTarget
import org.jetbrains.amper.dependency.resolution.attributes.hasKotlinPlatformType
import org.jetbrains.amper.dependency.resolution.attributes.hasKotlinWasmTarget
import org.jetbrains.amper.dependency.resolution.attributes.hasNoAttribute
import org.jetbrains.amper.dependency.resolution.attributes.isDocumentation
import org.jetbrains.amper.dependency.resolution.diagnostics.BomDeclaredAsRegularDependency
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.DependencyIsNotMultiplatform
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.FailedRepackagingKMPLibrary
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.KotlinMetadataHashNotResolved
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.KotlinMetadataMissing
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.KotlinMetadataNotResolved
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.KotlinProjectStructureMetadataMissing
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.ModuleFileNotDownloaded
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.MoreThanOneVariant
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.NoVariantForPlatform
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.PomWasFoundButMetadataIsMissing
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.PomWasNotFound
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.ProjectHasMoreThanTenAncestors
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToDetermineDependencyVersion
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToDetermineDependencyVersionForKotlinLibrary
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToParseMetadata
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnableToParsePom
import org.jetbrains.amper.dependency.resolution.diagnostics.DependencyResolutionDiagnostics.UnexpectedDependencyFormat
import org.jetbrains.amper.dependency.resolution.diagnostics.DiagnosticReporter
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.MetadataResolvedWithPomErrors
import org.jetbrains.amper.dependency.resolution.diagnostics.PlatformsAreNotSupported
import org.jetbrains.amper.dependency.resolution.diagnostics.PomResolvedWithMetadataErrors
import org.jetbrains.amper.dependency.resolution.diagnostics.RegularDependencyDeclaredAsBom
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
import org.jetbrains.amper.dependency.resolution.diagnostics.UnableToResolveDependency
import org.jetbrains.amper.dependency.resolution.diagnostics.asMessage
import org.jetbrains.amper.dependency.resolution.diagnostics.hasErrors
import org.jetbrains.amper.dependency.resolution.files.computeHash
import org.jetbrains.amper.dependency.resolution.files.produceFileWithDoubleLockAndHash
import org.jetbrains.amper.dependency.resolution.metadata.json.module.AvailableAt
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Capability
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Dependency
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Module
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Version
import org.jetbrains.amper.dependency.resolution.metadata.json.module.parseMetadata
import org.jetbrains.amper.dependency.resolution.metadata.json.projectStructure.KotlinProjectStructureMetadata
import org.jetbrains.amper.dependency.resolution.metadata.json.projectStructure.parseKmpLibraryMetadata
import org.jetbrains.amper.dependency.resolution.metadata.xml.Dependencies
import org.jetbrains.amper.dependency.resolution.metadata.xml.DependencyManagement
import org.jetbrains.amper.dependency.resolution.metadata.xml.Project
import org.jetbrains.amper.dependency.resolution.metadata.xml.expandTemplates
import org.jetbrains.amper.dependency.resolution.metadata.xml.localRepository
import org.jetbrains.amper.dependency.resolution.metadata.xml.parsePom
import org.jetbrains.amper.dependency.resolution.metadata.xml.parseSettings
import org.jetbrains.amper.dependency.resolution.metadata.xml.plus
import org.jetbrains.amper.telemetry.use
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

interface MavenDependencyNode : DependencyNode {
    val originalVersion: String?
    val versionFromBom: String?
    val isBom: Boolean

    val dependency: MavenDependency

    val overriddenBy: Set<DependencyNode>

    override val key: Key<MavenDependency>
        get() = Key<MavenDependency>("$group:$module")

    override val graphEntryName: String
        get() = if (dependency.version == originalVersion) {
            dependency.toString()
        } else {
            "$group:$module:${originalVersion.orUnspecified()} -> ${dependency.version}"
        }

    fun getOriginalMavenCoordinates(): MavenCoordinates = dependency.coordinates.copy(version = originalVersion)

    fun getMavenCoordinatesForPublishing(): MavenCoordinates

    fun getParentKmpLibraryCoordinates(): MavenCoordinates?

    override fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        MavenDependencyNodePlain(
            originalVersion, versionFromBom, isBom, messages,
            dependencyRef = dependency.toSerializableReference(graphContext),
            coordinatesForPublishing = getMavenCoordinatesForPublishing(),
            parentKmpLibraryCoordinates = getParentKmpLibraryCoordinates(),
            graphContext = graphContext
        )

    override fun fillEmptyNodePlain(nodePlain: DependencyNodePlain, graphContext: DependencyGraphContext, nodeReference: DependencyNodeReference?) {
        super.fillEmptyNodePlain(nodePlain, graphContext, nodeReference)

        val overriddenBy = overriddenBy
            .filter { !it.isOrphan(root = graphContext.allDependencyNodeReferences.entries.first().key) }
            .map { it.toSerializableReference(graphContext, null) }
        (nodePlain as MavenDependencyNodePlain).overriddenByRefs.addAll(overriddenBy)
    }
}

val MavenDependencyNode.group
    get() = dependency.group
val MavenDependencyNode.module
    get() = dependency.module

@Serializable
class MavenDependencyNodePlain internal constructor(
    override val originalVersion: String?,
    override val versionFromBom: String?,
    override val isBom: Boolean,
    override val messages: List<Message>,
    private val dependencyRef: MavenDependencyReference,
    override val parentsRefs: MutableSet<DependencyNodeReference> = mutableSetOf(),
    override val childrenRefs: MutableList<DependencyNodeReference> = mutableListOf(),
    internal val overriddenByRefs: MutableSet<DependencyNodeReference> = mutableSetOf(),
    private val coordinatesForPublishing: MavenCoordinates,
    private val parentKmpLibraryCoordinates: MavenCoordinates?,
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext()
) : MavenDependencyNode, DependencyNodePlainBase(graphContext) {
    override fun getMavenCoordinatesForPublishing(): MavenCoordinates = coordinatesForPublishing
    override fun getParentKmpLibraryCoordinates(): MavenCoordinates? = parentKmpLibraryCoordinates

    override val overriddenBy: Set<DependencyNode> by lazy { overriddenByRefs.map { it.toNodePlain(graphContext) }.toSet() }
    override val dependency: MavenDependency by lazy { dependencyRef.toNodePlain(graphContext) }
}

/**
 * Serves as a holder for a dependency defined by Maven coordinates, namely, group, module, and version.
 * While each node in a graph is expected to be unique, its [dependency] can be shared across other nodes
 * as long as their groups and modules match.
 * A version discrepancy might occur if a conflict resolution algorithm intervenes and is expected.
 *
 * The node doesn't do actual work but simply delegate to the [dependency] that can change over time.
 * This allows reusing dependency resolution results but still holding information about the origin.
 *
 * It's the responsibility of the caller to set a parent for this node if none was provided via the constructor.
 *
 * @see [DependencyNodeHolderImpl]
 */
class MavenDependencyNodeImpl internal constructor(
    templateContext: Context,
    dependency: MavenDependencyImpl,
    parentNodes: Set<DependencyNodeWithResolutionContext> = emptySet(),
) : MavenDependencyNode, DependencyNodeWithResolutionContext {

    override val parents: Set<DependencyNode> get() = context.nodeParents

    constructor(
        templateContext: Context,
        group: String,
        module: String,
        version: String?,
        isBom: Boolean,
        parentNodes: Set<DependencyNodeWithResolutionContext> = emptySet(),
    ) : this(
        templateContext,
        templateContext.createOrReuseDependency(group, module, version, isBom),
        parentNodes,
    )

    @Volatile
    override var dependency: MavenDependencyImpl = dependency
        set(value) {
            assert(group == value.group) { "Groups don't match. Expected: $group, actual: ${value.group}" }
            assert(module == value.module) { "Modules don't match. Expected: $module, actual: ${value.module}" }
            field = value
        }

    override val originalVersion: String? = dependency.version

    override val isBom: Boolean = dependency.isBom

    /**
     * This version taken from imported BOM is set to this field by dependency resolution (on the build graph stage)
     * for those nodes having an original version unspecified.
     *
     * Resolved node version is taken from [dependency] anyway, since it could be overridden during conflict resolution.
     * This field is used as additional information representing the intermediate version taken from BOM.
     */
    override var versionFromBom: String? = null
        internal set

    override var overriddenBy: Set<DependencyNode> = emptySet()
        internal set

    override val context: Context = templateContext.copyWithNewNodeCache(parentNodes)
    override val children: List<DependencyNodeWithResolutionContext> by PropertyWithDependencyGeneric(
        dependencyProviders = listOf(
            { thisRef: MavenDependencyNodeImpl -> thisRef.dependency.children },
            { thisRef: MavenDependencyNodeImpl -> thisRef.dependency.dependencyConstraints }
        ),
        valueProvider = { dependencies ->
            val children = dependencies[0] as List<*>
            val dependencyConstraints = dependencies[1] as List<*>
            children.map { it as MavenDependencyImpl }.mapNotNull {
                context
                    .getOrCreateNode(it, this)
                    // skip children that form cyclic dependencies
                    .let { child ->
                        child.takeIf { !child.isDescendantOf(child) }
                            .also { if (it == null) child.context.nodeParents.remove(this) }
                    }
            } + dependencyConstraints.map {
                context.getOrCreateConstraintNode(it as MavenDependencyConstraintImpl, this)
            }
        },
        onValueRecalculated = { oldValue, newValue ->
            if (oldValue != null) {
                (oldValue - newValue.toSet()).forEach { it.context.nodeParents.remove(this) }
            }
        }
    )

    override val messages: List<Message>
        get() = dependency.messages

    override suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean) {
        if (transitive) {
            dependency.resolveChildren(context, level)
        }
    }

    override suspend fun downloadDependencies(downloadSources: Boolean) =
        context.spanBuilder("MavenDependencyNode.downloadDependencies")
            .setAttribute("coordinates", dependency.toString())
            .use {
                dependency.downloadDependencies(context, downloadSources)
            }

    override fun toString(): String = if (dependency.version == originalVersion) {
        dependency.toString()
    } else {
        "$group:$module:${originalVersion.orUnspecified()} -> ${dependency.version}"
    }

    private fun DependencyNode.isDescendantOf(
        parent: DependencyNode,
        visited: MutableSet<DependencyNode> = mutableSetOf(),
    ): Boolean {
        return (parents - visited)
            .let {
                visited.addAll(it)
                it.any { it.key == parent.key }
                        || it.any { it.isDescendantOf(parent, visited) }
            }
    }

    /**
     * NB: This method currently works properly only in the single-platform resolution context.
     *
     * todo (AB) : Replace with proper isKmpLibrary() condition that takes multi-platform context into account
     */
    fun isKmpLibrary(): Boolean = when {
        context.settings.platforms.size == 1 -> dependency.files(false).isEmpty()
        else -> false
    }

    /**
     * For a platform-specific artifact introduced into the resolution by a KMP library
     * (as one of its `available-at`), this method returns the coordinates of such a KMP library.
     *
     * This method is useful for IDE to find out if the library can be introduced to a module by depending on the
     * original multiplatform library (e.g., if the symbol from the platform-specific part was found).
     *
     * This is the counterpart of [getMavenCoordinatesForPublishing].
     *
     * If the current node is not a platform-specific variant of a KMP library, `null` is returned.
     */
    override fun getParentKmpLibraryCoordinates(): MavenCoordinates? {
        // When the node is resolved in the multiplatform context, it's unlikely that any platforms-specific artifacts
        // will be resolved for KMP libraries. Thus, there will be no nodes to call this method on.
        if (context.settings.platforms.size != 1) return null

        val thisCoordinates = dependency.coordinates
        for (parent in parents) {
            if (parent !is MavenDependencyNodeImpl) continue

            if (parent.isKmpLibrary()) {
                val availableAtCoordinates = with(parent.dependency) { variants.withoutDocumentationAndMetadata }
                    .mapNotNull { it.`available-at`?.toCoordinates() }

                if (availableAtCoordinates.any { it == thisCoordinates }) {
                    return parent.dependency.coordinates
                }
            }
        }

        return null
    }

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
    override fun getMavenCoordinatesForPublishing(): MavenCoordinates {
        if (context.settings.platforms.size == 1 && isKmpLibrary()) {
            val childrenOriginalCoordinates = children
                .filterIsInstance<MavenDependencyNodeImpl>()
                .mapNotNull { nested ->
                    nested.originalVersion()?.let { nested.dependency.coordinates.copy(version = it) }
                }
            val singlePlatformCoordinates = with(dependency) { variants.withoutDocumentationAndMetadata }
                .mapNotNull { it.`available-at`?.toCoordinates() }
                .singleOrNull { it in childrenOriginalCoordinates }

            if (singlePlatformCoordinates != null) return singlePlatformCoordinates
        }

        return getOriginalMavenCoordinates()
    }
}

interface MavenDependencyConstraintNode : DependencyNode {
    val group: String
    val module: String
    val version: Version

    val dependencyConstraint: MavenDependencyConstraint

    val overriddenBy: Set<DependencyNode>

    override val key: Key<*> get() = Key<MavenDependency>("$group:$module") // reusing the same key as MavenDependencyNode

    override val graphEntryName: String
        get() = if (dependencyConstraint.version == version) {
            "$group:$module:${version.resolve()}"
        } else {
            "$group:$module:${version.asString()} -> ${dependencyConstraint.version.asString()}"
        }

    override fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        MavenDependencyConstraintNodePlain(
            group, module, version,
            dependencyConstraintRef = dependencyConstraint.toSerializableReference(graphContext),
            messages = messages,
            graphContext = graphContext,
        )

    override fun fillEmptyNodePlain(nodePlain: DependencyNodePlain, graphContext: DependencyGraphContext, nodeReference: DependencyNodeReference?) {
        super.fillEmptyNodePlain(nodePlain, graphContext, nodeReference)

        val overriddenBy = overriddenBy
            .filter { !it.isOrphan(root = graphContext.allDependencyNodeReferences.entries.first().key) }
            .map { it.toSerializableReference(graphContext, null) }
        (nodePlain as MavenDependencyConstraintNodePlain).overriddenByRefs.addAll(overriddenBy)
    }
}

@Serializable
class MavenDependencyConstraintReference(
    val index: Int
) {
    fun toNodePlain(graphContext: DependencyGraphContext): MavenDependencyConstraint =
        graphContext.getMavenDependencyConstraint(index)
}


@Serializable
class MavenDependencyConstraintNodePlain internal constructor(
    override val group: String,
    override val module: String,
    override val version: Version,
    private val dependencyConstraintRef: MavenDependencyConstraintReference,
    override val parentsRefs: MutableSet<DependencyNodeReference> = mutableSetOf(),
    override val childrenRefs: List<DependencyNodeReference> = mutableListOf(),
    internal val overriddenByRefs: MutableSet<DependencyNodeReference> = mutableSetOf(),
    override val messages: List<Message>,
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext()
) : MavenDependencyConstraintNode, DependencyNodePlainBase(graphContext) {

    override val dependencyConstraint: MavenDependencyConstraint by lazy { dependencyConstraintRef.toNodePlain(graphContext) }

    override val overriddenBy: Set<DependencyNode> by lazy { overriddenByRefs.map { it.toNodePlain(graphContext) }.toSet() }

}

internal class MavenDependencyConstraintNodeImpl internal constructor(
    templateContext: Context,
    dependencyConstraint: MavenDependencyConstraintImpl,
    parentNodes: Set<DependencyNodeWithResolutionContext> = emptySet(),
):  MavenDependencyConstraintNode, DependencyNodeWithResolutionContext {
    @Volatile
    override var dependencyConstraint: MavenDependencyConstraintImpl = dependencyConstraint
        set(value) {
            assert(group == value.group) { "Groups don't match. Expected: $group, actual: ${value.group}" }
            assert(module == value.module) { "Modules don't match. Expected: $module, actual: ${value.module}" }
            field = value
        }

    override val group: String = dependencyConstraint.group
    override val module: String = dependencyConstraint.module
    override val version: Version = dependencyConstraint.version

    override var overriddenBy: Set<DependencyNode> = emptySet()
        internal set

    override val context: Context = templateContext.copyWithNewNodeCache(parentNodes)
    override val children: List<DependencyNodeWithResolutionContext> = emptyList()
    override val messages: List<Message> = emptyList()


    override suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean) {}

    override suspend fun downloadDependencies(downloadSources: Boolean) {}

    override fun toString(): String = graphEntryName
}

private typealias DependencyProvider<T> = (T) -> Any?

/**
 * A lazy property that's recalculated if its dependency changes.
 */
private class PropertyWithDependencyGeneric<T, V : Any>(
    val dependencyProviders: List<DependencyProvider<T>>,
    val valueProvider: (List<Any?>) -> V,
    val onValueRecalculated: (V?, V) -> Unit = { _, _ -> },
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
                    val oldValue = if (::value.isInitialized) value else null
                    value = valueProvider(dependencies)
                    onValueRecalculated(oldValue, value)
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
): MavenDependencyImpl =
    this.resolutionCache.computeIfAbsent(Key<MavenDependencyImpl>("$group:$module:${version.orUnspecified()}:$isBom")) {
        MavenDependencyImpl(this.settings, group, module, version, isBom)
    }

internal fun Context.createOrReuseDependencyConstraint(
    group: String,
    module: String,
    version: Version
): MavenDependencyConstraintImpl =
    this.resolutionCache.computeIfAbsent(Key<MavenDependencyConstraintImpl>("$group:$module:$version")) {
        MavenDependencyConstraintImpl(group, module, version)
    }

interface MavenDependencyConstraint {
    val group: String
    val module: String
    val version: Version

    fun toSerializableReference(graphContext: DependencyGraphContext): MavenDependencyConstraintReference {
        return graphContext.getMavenDependencyConstraintReference(this)
            ?: run {
                // 1. register empty reference first (to break cycles)
                val newNodePlain = MavenDependencyConstraintPlain(group, module, version)
                val newReference = graphContext.registerMavenDependencyConstraintPlain(this, newNodePlain)
                newReference
            }
    }
}

data class MavenDependencyConstraintImpl(
    override val group: String,
    override val module: String,
    override val version: Version
) : MavenDependencyConstraint

@Serializable
data class MavenDependencyConstraintPlain(
    override val group: String,
    override val module: String,
    override val version: Version
) : MavenDependencyConstraint

internal fun shouldIgnoreDependency(group: String, module: String, context: Context): Boolean {
    return MavenGroupAndArtifact(group, module) in context.settings.dependenciesBlocklist
}

/**
 * The name of the KMP source set represented by the given dependency file.
 */
val KmpSourceSetName = Key<String>("KmpSourceSetName")

/**
 * Represents the list of platforms supported by the given source set.
 *
 * It is required for IDE to know about the capabilities of the dependency, which allows properly suggesting it
 * for adding as a dependency found by the unresolved reference search.
 */
val KmpPlatforms = Key<Set<ResolutionPlatform>>("KmpPlatforms")

interface MavenDependency {
    val coordinates: MavenCoordinates
    val packaging: String?
    val resolutionConfig: ResolutionConfig
    val messages: List<Message>
    val state: ResolutionState

    fun files(withSources: Boolean = false): List<DependencyFile>

    fun toSerializableReference(graphContext: DependencyGraphContext): MavenDependencyReference {
        // 1. register plain
        // 2. enrich it with references

        return graphContext.getMavenDependencyReference(this)
            ?: run {
                val mavenDependencyPlain = MavenDependencyPlain(
                    coordinates,
                    packaging,
                    messages,
                    files(true).map { DependencyFilePlain(it) },
                    // todo (AB) : ResolutionConfigPlain could be deduplicated with reference
                    ResolutionConfigPlain(
                        resolutionConfig.scope, resolutionConfig.platforms,
                        // todo (AB) : ResolutionConfigPlain could be deduplicated with reference
                        resolutionConfig.repositories
                    ),
                    state
                    // todo (AB) : Could be deduplicated
                )
                graphContext.registerMavenDependencyPlain(this, mavenDependencyPlain)
            }
    }
}

val MavenDependency.group
    get() = coordinates.groupId
val MavenDependency.module
    get() = coordinates.artifactId
val MavenDependency.version
    get() = coordinates.version

@Serializable
class MavenDependencyPlain internal constructor (
    override val coordinates: MavenCoordinates,
    override val packaging: String?,
    override val messages: List<Message>,
    val files: List<DependencyFilePlain>,
    override val resolutionConfig: ResolutionConfigPlain,
    override val state: ResolutionState = ResolutionState.RESOLVED
) : MavenDependency {

    override fun files(withSources: Boolean): List<DependencyFile> {
        return if (withSources) files else files.filterNot { it.isDocumentation }
    }

    override fun toString() = "$group:$module:${version.orUnspecified()}"
}

@Serializable
class MavenDependencyReference(
    private val index: MavenDependencyIndex
) {
    fun toNodePlain(graphContext: DependencyGraphContext): MavenDependency =
        graphContext.getMavenDependency(index)
}

/**
 * An actual Maven dependency that can be resolved, that is, populated with children according to the requested
 * [ResolutionScope] and platform.
 * That means MavenDependency is bound to dependency resolution context, i.e., the instance of the class resolved for one context
 * could not be reused in another one.
 * Its [resolve] method contains the resolution algorithm.
 *
 * @see [DependencyFile]
 */
class MavenDependencyImpl internal constructor(
    val settings: Settings,
    override val coordinates: MavenCoordinates,
    val isBom: Boolean
): MavenDependency {
    internal constructor(
        settings: Settings,
        groupId: String,
        artifactId: String,
        version: String?,
        isBom: Boolean = false,
    ) : this(settings, MavenCoordinates(groupId, artifactId, version), isBom)

    override val resolutionConfig: ResolutionConfig
        get() = settings

    @Volatile
    override var state: ResolutionState = ResolutionState.INITIAL
        private set

    @Volatile
    internal var variants: List<Variant> = listOf()

    @Volatile

    internal var sourceSetsFiles: List<DependencyFileImpl> = listOf()
        private set

    @Volatile
    override var packaging: String? = null
        private set

    @Volatile
    var children: List<MavenDependencyImpl> = listOf()
        private set

    @Volatile
    internal var dependencyConstraints: List<MavenDependencyConstraintImpl> = listOf()
        private set

    internal var metadataResolutionFailureMessage: Message? = null

    override val messages: List<Message>
        get() = if (version == null) {
            listOf(DependencyResolutionDiagnostics.UnspecifiedDependencyVersion.asMessage("$group:$module"))
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

    override fun files(withSources: Boolean) =
        if (withSources)
            _files
        else
            _filesWithoutSources.filterNot { it.hasSourcesFilename() }

    private fun DependencyFileImpl.hasSourcesFilename(): Boolean =
        fileName.endsWith("-sources.jar") || fileName.endsWith("-javadoc.jar")

    private val _files: List<DependencyFileImpl> by filesProvider(withSources = true)
    private val _filesWithoutSources: List<DependencyFileImpl> by filesProvider(withSources = false)

    private fun filesProvider(withSources: Boolean) =
        PropertyWithDependencyGeneric(
            dependencyProviders = listOf(
                { thisRef: MavenDependencyImpl -> thisRef.variants },
                { thisRef: MavenDependencyImpl -> thisRef.packaging },
                { thisRef: MavenDependencyImpl -> thisRef.sourceSetsFiles }
            ),
            valueProvider = { dependencies ->
                val variants = dependencies[0] as List<*>
                val packaging = dependencies[1] as String?
                val sourceSetsFiles = dependencies[2] as List<*>
                val dependency = this@MavenDependencyImpl
                buildList {
                    variants
                        .map { it as Variant }
                        .let { if (withSources) it else it.withoutDocumentationAndMetadata }
                        .let {
                            val areSourcesMissing = withSources && it.documentationOnly.isEmpty()
                            it.forEach { variant ->
                                val isDocumentationOrMetadata = variant.isDocumentationOrMetadata
                                variant.files.forEach {
                                    add(getDependencyFile(dependency,it, isDocumentation = isDocumentationOrMetadata))
                                    if (areSourcesMissing) {
                                        add(getAutoAddedSourcesDependencyFile())
                                    }
                                }
                            }
                        }
                    packaging?.takeIf { it != "pom" }?.let {
                        val nameWithoutExtension = getNameWithoutExtension(dependency)
                        val extension = if (it == "bundle" || it.endsWith("-plugin")) "jar" else it
                        add(getDependencyFile(dependency, nameWithoutExtension, extension))
                        if (extension == "jar" && withSources) {
                            add(getAutoAddedSourcesDependencyFile())
                        }
                    }
                    addAll(sourceSetsFiles.map { it as DependencyFileImpl })
                }
            }
        )

    /**
     * The repository module/pom file was downloaded from.
     * If we download a module/pom file from some repository,
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
                    context.spanBuilder("MavenDependencyNode.resolveChildren")
                        .setAttribute("coordinates", this.toString())
                        .use {
                            resolve(context, level)
                        }
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
                metadataResolutionFailureMessage = UnableToResolveDependency(
                    coordinates = this.coordinates,
                    repositories = context.settings.repositories,
                    resolutionLevel = level,
                    childMessages = pom.diagnosticsReporter.getMessages() + moduleFile.diagnosticsReporter.getMessages(),
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

    private fun List<Variant>.filterWellKnowSpecialLibraries(group: String, module: String): List<Variant> {
        if (withoutDocumentationAndMetadata.size <= 1) return this

        if (group == "org.jetbrains.kotlin"
            && (module == "kotlin-gradle-plugin" || module == "fus-statistics-gradle-plugin")
        ) {
            val nonVersionVariants = filter { it.hasNoAttribute(PluginApiVersion) }
            if (nonVersionVariants.withoutDocumentationAndMetadata.size == 1) return nonVersionVariants
        }

        return this
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
        diagnosticsReporter: DiagnosticReporter,
    ) {
        val moduleMetadata = parseModuleMetadata(context, level, diagnosticsReporter, true)
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
                diagnosticsReporter.addMessage(RegularDependencyDeclaredAsBom(this.coordinates))
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
                    reportVariantMismatchForLibrary(diagnosticsReporter, moduleMetadata, setOf(platform))
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
                                    level,
                                    diagnosticsReporter
                                ) + listOfNotNull(it.`available-at`?.asDependency())
                            }.map {
                                it.toMavenDependency(context, diagnosticsReporter)
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
                    ) ?: (null to null)

                if (kotlinMetadataVariant != null && kmpMetadataFile != null) {
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
                            ?.let { getDependencyFile(this, it, isDocumentation = true) }
                            ?: getAutoAddedSourcesDependencyFile()

                    sourceSetsFiles = sourceSetsFiles.toMutableList() + listOf(sourcesDependencyFile)
                } else {
                    // source sets were not resolved, a corresponding error was reported (apart from an exceptional case of jvm+android)
                }
            }
        }

        state = level.state

        // We have used a module metadata file for resolving dependency, => lower severity of pom-related issues.
        if (pom.diagnosticsReporter.hasErrors()) {
            pom.diagnosticsReporter.suppress { suppressedMessages ->
                MetadataResolvedWithPomErrors(coordinates, suppressedMessages)
            }
        }
    }

    private fun reportVariantMismatchForLibrary(
        diagnosticsReporter: DiagnosticReporter,
        module: Module,
        missingPlatforms: Set<ResolutionPlatform>,
    ) {
        val groupedByCategory = module.variants.groupBy { it.getAttributeValue(Category) }
        val libraryVariants = buildList {
            groupedByCategory[Category.Library]?.let { addAll(it) }
            // Old versions of Kotlin libraries might not have the proper category attribute set.
            groupedByCategory[null]?.let { addAll(it) }
        }
        if (libraryVariants.isEmpty() && groupedByCategory[Category.Platform] != null) {
            diagnosticsReporter.addMessage(BomDeclaredAsRegularDependency(this.coordinates))
            return
        }

        val supportedPlatforms = libraryVariants
            .mapNotNullTo(mutableSetOf()) { variant -> getLeafPlatformFromVariant(variant) }
        // This check might be more sophisticated,
        // but for the time being we suppose that all JVM libraries can be used on Android
        if (ResolutionPlatform.JVM in supportedPlatforms) {
            supportedPlatforms += ResolutionPlatform.ANDROID
        }
        if (supportedPlatforms.isNotEmpty()) {
            diagnosticsReporter.addMessage(PlatformsAreNotSupported(this, supportedPlatforms))
            return
        }

        // It's a fallback that actually shouldn't ever occur. It is possible that it can be safely deleted.
        missingPlatforms.forEach {
            diagnosticsReporter.addMessage(NoVariantForPlatform.asMessage(it.pretty, this))
        }
    }

    private fun getLeafPlatformFromVariant(variant: Variant): ResolutionPlatform? {
        val kotlinPlatform = variant.getAttributeValue(KotlinPlatformType) as? KotlinPlatformType.Known ?: return null
        return when (kotlinPlatform.platformType) {
            PlatformType.COMMON -> null
            PlatformType.JVM -> ResolutionPlatform.JVM
            PlatformType.ANDROID_JVM -> ResolutionPlatform.ANDROID
            PlatformType.JS -> ResolutionPlatform.JS
            PlatformType.WASM -> {
                val wasmTarget =
                    variant.getAttributeValue(KotlinWasmTarget) as? KotlinWasmTarget.Known ?: return null
                wasmTarget.platform
            }

            PlatformType.NATIVE -> {
                val nativeTarget =
                    variant.getAttributeValue(KotlinNativeTarget) as? KotlinNativeTarget.Known ?: return null
                nativeTarget.platform
            }
        }
    }

    fun getAutoAddedSourcesDependencyFile() =
        getDependencyFile(
            this,
            "${this.moduleFile.nameWithoutExtension}-sources",
            "jar",
            isDocumentation = true,
            isAutoAddedDocumentation = true
        )

    /**
     * Some pretty basic libraries that are used in a KMP world mimic for pure JVM libraries,
     * those cases should be processed in a custom way.
     *
     * If this method returns true, it means such a library was detected and processed
     * (library dependencies are registered to graph); no further processing by a usual way is needed.
     *
     * @return true, in case a Kmp library that needs special treatment was detected and processed, false - otherwise
     * @see isSpecialKmpLibrary
     */
    private suspend fun processSpecialKmpLibraries(
        context: Context,
        moduleMetadata: Module,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter,
    ): Boolean {
        if (isSpecialKmpLibrary()) {
            moduleMetadata
                .variants
                .let {
                    it.flatMap {
                        it.dependencies(context, level, diagnosticsReporter)
                    }.map {
                        it.toMavenDependency(context, diagnosticsReporter)
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

    private fun Dependency.toMavenDependencyConstraint(context: Context): MavenDependencyConstraintImpl? {
        return version?.let { context.createOrReuseDependencyConstraint(group, module, version) }
    }

    private fun Dependency.toMavenDependency(
        context: Context,
        reportError: (reason: String) -> Unit = {},
    ): MavenDependencyImpl {
        val resolvedVersion = resolveVersion(reportError)
        return context.createOrReuseDependency(group, module, resolvedVersion, isBom())
    }

    private fun Dependency.toMavenDependency(
        context: Context,
        diagnosticsReporter: DiagnosticReporter,
    ): MavenDependencyImpl {
        val dependency = this
        return toMavenDependency(context) { reason ->
            val coordinates = this@MavenDependencyImpl.coordinates
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
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter,
    ): List<MavenDependencyConstraintImpl> =
        dependencies.mapNotNull {
            if (!it.isBom()) {
                null
            } else {
                withResolvedVersion(it)
                    .takeIf { it.version != null }
                    ?.toMavenDependency(context, diagnosticsReporter)
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
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter
    ): List<Dependency> =
        dependencies.filterNot { shouldIgnoreDependency(it.group, it.module, context) }.map {
            withResolvedVersion(it) { bomDependencyConstraints(context, level, diagnosticsReporter) }
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
        bomDependencyConstraints: suspend () -> List<MavenDependencyConstraintImpl> = { emptyList() }
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
            // Empty version of transitive dependency might be resolved from some BOM file from dependency graph
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
        diagnosticReporter: DiagnosticReporter,
        skipIsDownloadedCheck: Boolean = false,
    ): Module? {
        if (skipIsDownloadedCheck || moduleFile.isDownloadedOrDownload(level, context, diagnosticReporter)) {
            try {
                return moduleFile.readText().parseMetadata()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = UnableToParseMetadata.asMessage(
                    moduleFile,
                    extra = DependencyResolutionBundle.message("extra.exception", e),
                    exception = e,
                )
                logger.warn(message.message, e)
                diagnosticReporter.addMessage(message)
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
        moduleMetadata: Module,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter,
    ): Pair<Variant, DependencyFileImpl>? {
        val kotlinMetadataVariant =
            getKotlinMetadataVariant(moduleMetadata.variants, platform, context, diagnosticsReporter)
                ?: return null  // the children list is empty in case kmp common variant is not resolved
        val kotlinMetadataFile = getKotlinMetadataFile(kotlinMetadataVariant, diagnosticsReporter)
            ?: return null  // the children list is empty if kmp common variant file is not resolved

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
                        childMessages = kmpMetadataDependencyFile.diagnosticsReporter.getMessages(),
                    )
                )
                null
            }
    }

    private suspend fun resolveKmpLibrary(
        kmpMetadataFile: DependencyFileImpl,
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

        val resolvedVariants = context.settings.platforms.associateWith {
            resolveVariants(moduleMetadata, context.settings, it).withoutDocumentationAndMetadata
        }
        val platformsWithoutVariants = resolvedVariants.filter { it.value.isEmpty() }
        if (platformsWithoutVariants.isNotEmpty()) {
            reportVariantMismatchForLibrary(diagnosticsReporter, moduleMetadata, platformsWithoutVariants.keys)
        }

        val allPlatformsVariants = resolvedVariants.values.flatten().associateBy { it.name }

        val allSourceSetNames = kotlinProjectStructureMetadata.projectStructure.sourceSets.map { it.name }

        // Selecting source sets related to target platforms (intersection).
        val sourceSetsIntersection = kotlinProjectStructureMetadata.projectStructure.variants
            .filter { it.name in (allPlatformsVariants.keys + allPlatformsVariants.keys.map { it.removeSuffix("-published") }) }
            .map { it.sourceSet.toSet() }
            .let {
                if (it.isEmpty()) emptySet() else it.reduce { l1, l2 -> l1.intersect(l2) }
            }
            .filter { it in allSourceSetNames }.toSet()

        val allMatchingSourceSetNames = sourceSetsIntersection.toMutableSet()
        resolveCinteropSourceSet(sourceSetsIntersection, kotlinProjectStructureMetadata)?.let {
            allMatchingSourceSetNames.add(it)
        }

        // Transforming it right here, since it doesn't require network access in most cases.
        sourceSetsFiles = coroutineScope {
            allMatchingSourceSetNames
                .map {
                    async(Dispatchers.IO) {
                        toDependencyFile(
                            sourceSetName = it,
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
                    kotlinMetadataVariant.dependencies(context, level, diagnosticsReporter)
                        .filterNot { shouldIgnoreDependency(it.group, it.module, context) }
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

    /**
     * Cinterop source set is packaged differently from the regular sourceSets.
     * Each cinterop sourceSet has all available APIs.
     * For instance, macosSourceSet would have not only macOS-related API, but also apple, native and common ones.
     *
     * This way we have to select a single cinterop sourceSet - the most specific one matching target platforms.
     *
     * The code is inspired by the similar code from KGP plugin:
     * https://jetbrains.team/p/kt/repositories/kotlin/files/e8edf53610f82ecabfc2f8ae7aea8dc6f68409f2/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/targets/native/internal/CInteropMetadataDependencyClasspath.kt?tab=source&line=66&lines-count=1
     */
    private fun resolveCinteropSourceSet(
        sourceSetNames: Set<String>,
        kotlinProjectStructureMetadata: KotlinProjectStructureMetadata,
    ): String? {
        val sourceSetsWithCinterop = kotlinProjectStructureMetadata.projectStructure.sourceSets
            .filter { it.name in sourceSetNames && it.sourceSetCInteropMetadataDirectory != null }

        // All dependencies of given sourceSets
        val dependsOnSourceSets = sourceSetsWithCinterop
            .flatMap { it.dependsOn }
            .toSet()

        // Choose a sourceSet that nobody depends on from the list of matching sourceSets with cinterop,
        // such a source set contains the reachest API surface available for target platforms.
        val bottomSourceSets = sourceSetsWithCinterop.filter { it.name !in dependsOnSourceSets }.toSet()

        // Select the source set participating in the least number of variants (the most special one)
        val cinteropSourceSet = bottomSourceSets.minByOrNull { sourceSet ->
            kotlinProjectStructureMetadata.projectStructure.variants.count {
                sourceSet.name in it.sourceSet
            }
        }

        return cinteropSourceSet?.sourceSetCInteropMetadataDirectory
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
        context: Context,
        diagnosticReporter: DiagnosticReporter,
    ): Variant? {
        if (this.isKotlinTestAnnotationsCommon()) return null
        val metadataVariants = validVariants.filter { it.isKotlinMetadata(platform) }
        return metadataVariants.firstOrNull() ?: run {
            val resolvingForJvm =
                context.settings.platforms == setOf(ResolutionPlatform.JVM, ResolutionPlatform.ANDROID)
            diagnosticReporter.addMessage(
                DependencyIsNotMultiplatform.asMessage(
                    this,
                    overrideSeverity = Severity.WARNING.takeIf { resolvingForJvm }
                )
            )
            null
        }
    }

    private fun getKotlinMetadataSourcesVariant(
        validVariants: List<Variant>,
        platform: ResolutionPlatform,
    ): Variant? {
        if (this.isKotlinTestAnnotationsCommon()) return null
        return validVariants.firstOrNull { it.isKotlinMetadataSources(platform) }
    }

    // todo (AB) : All this logic might be wrapped into ExecuteOnChange
    // todo (AB) : in order to skip metadata resolution in case targetFile exists already
    private suspend fun toDependencyFile(
        sourceSetName: String,
        kmpMetadataFile: DependencyFileImpl,
        moduleMetadata: Module,
        kotlinProjectStructureMetadata: KotlinProjectStructureMetadata? = null, // is empty for sources only
        context: Context,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter,
    ): DependencyFileImpl? {
        val group = kmpMetadataFile.dependency.group
        val module = kmpMetadataFile.dependency.module
        val version = kmpMetadataFile.dependency.version
        // kmpMetadataFile hash
        val sha1 = context.spanBuilder("toDependencyFile -> getExpectedHash")
            .setAttribute("fileName", kmpMetadataFile.fileName)
            .use {
                kmpMetadataFile.getExpectedHash("sha1", context.settings)
            }
            ?: kmpMetadataFile.getPath()?.let { path ->
                context.spanBuilder("toDependencyFile -> computeHash")
                    .setAttribute("fileName", kmpMetadataFile.fileName)
                    .use {
                        computeHash(path, "sha1").hash
                    }
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
        val kmpLibraryWithSourceSet = context.spanBuilder("resolveKmpLibraryWithSourceSet")
            .use {
                resolveKmpLibraryWithSourceSet(
                    sourceSetName,
                    kmpMetadataFile,
                    context,
                    kotlinProjectStructureMetadata,
                    moduleMetadata,
                    level,
                    diagnosticsReporter
                ) ?: run {
                    logger.debug("Source set $sourceSetName for Kotlin Multiplatform library ${kmpMetadataFile.fileName} is not found")
                    return@use null
                }
            } ?: return null

        val isSources = kmpMetadataFile.hasSourcesFilename()

        val extension = if (isSources) "jar" else "klib"
        val sourcesSuffix = if (isSources) "-sources" else ""

        // todo (AB) : Add test for downloading of KMP library sources of SNAPSHOT version
        // todo (AB) : (before the change such sources were incorrectly represented with regular DependencyFile)
        val sourceSetFile = getDependencyFile(
            kmpMetadataFile.dependency,
            "${kmpMetadataFile.dependency.module}-$sourceSetName$sourcesSuffix",
            extension,
            isDocumentation = isSources
        )
        sourceSetFile.settings[KmpSourceSetName] = sourceSetName
        if (kotlinProjectStructureMetadata != null) {
            sourceSetFile.settings[KmpPlatforms] = retrievePlatforms(
                kotlinProjectStructureMetadata,
                moduleMetadata,
                sourceSetName,
            )
        }

        val targetFileName = "$module-$sourceSetName-$version$sourcesSuffix.$extension"

        val targetPath = kmpMetadataFile.dependency.settings.fileCache.amperCache
            .resolve("kotlin/kotlinTransformedMetadataLibraries/")
            .resolve(group)
            .resolve(module)
            .resolve(version.orUnspecified())
            .resolve(sha1)
            .resolve(targetFileName)

        context.spanBuilder("produceFileWithDoubleLockAndHash").use {
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
                    val message = FailedRepackagingKMPLibrary.asMessage(
                        kmpLibraryWithSourceSet.name,
                        extra = DependencyResolutionBundle.message("extra.exception", e),
                        exception = e,
                    )
                    logger.warn(message.message, e)
                    diagnosticsReporter.addMessage(message)
                    false
                }
            }
        }

        sourceSetFile.onFileDownloaded(targetPath)
        return sourceSetFile
    }

    /**
     * Retrieves all platforms supported by the given [sourceSetName] in the dependency.
     */
    private fun retrievePlatforms(
        kotlinProjectStructureMetadata: KotlinProjectStructureMetadata,
        moduleMetadata: Module,
        sourceSetName: String,
    ): Set<ResolutionPlatform> {
        val sourceSetVariants = kotlinProjectStructureMetadata.projectStructure
            .variants
            .filter { sourceSetName in it.sourceSet }
            .map { it.name }
        return moduleMetadata.variants
            .filter { it.name.removeSuffix("-published") in sourceSetVariants }
            .mapNotNull { getLeafPlatformFromVariant(it) }
            .toSet()
    }

    /**
     * This method returns kotlin metadata library that contains given sourceSet.
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
     *  Each platform-specific dependency of the ui-uikit common library
     *  (for instance, in org.jetbrains.compose.ui:ui-uikit-uikitarm64:1.6.10)
     *  (https://maven.pkg.jetbrains.space/public/p/compose/dev/org/jetbrains/compose/ui/ui-uikit-uikitarm64/1.6.10/ui-uikit-uikitarm64-1.6.10-metadata.jar)
     */
    private suspend fun resolveKmpLibraryWithSourceSet(
        sourceSetName: String,
        kmpMetadataFile: DependencyFileImpl,
        context: Context,
        kotlinProjectStructureMetadata: KotlinProjectStructureMetadata?,
        moduleMetadata: Module,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter,
    ) = if (hasJarEntry(kmpMetadataFile.getPath()!!, sourceSetName) == true) {
        kmpMetadataFile.getPath()!!
    } else if (kotlinProjectStructureMetadata == null) {
        null
    } else {
        val contextApplePlatforms = allApplePlatforms.intersect(context.settings.platforms)
        if (contextApplePlatforms.isNotEmpty()) {
            // 1. Find names of all variants that declare this sourceSet
            val variantsWithSourceSet = kotlinProjectStructureMetadata.projectStructure.variants
                .filter { it.sourceSet.contains(sourceSetName.removeSuffix("-cinterop")) }
                .map { it.name }

            // 2. Find Apple variants for actual Apple platforms
            val appleVariants = contextApplePlatforms.flatMap { platform ->
                resolveVariants(moduleMetadata, context.settings, platform)
                    .withoutDocumentationAndMetadata
                    .map { it to platform }
            }

            appleVariants.firstOrNull {
                // 3. Filter the first variant that declares sourceSet
                it.first.name.removeSuffix("-published") in variantsWithSourceSet
            }?.let {
                val platform = it.second
                // 4. Try to download artifact from that variant and resolve dependency from that
                it.first
                    .`available-at`
                    ?.asDependency()
                    ?.toMavenDependency(context, diagnosticsReporter)
                    ?.let { dependency ->
                        val depModuleMetadata = dependency.parseModuleMetadata(context, level, diagnosticsReporter)
                        depModuleMetadata?.let {
                            dependency.detectKotlinMetadataLibrary(
                                context,
                                platform,
                                depModuleMetadata,
                                level,
                                diagnosticsReporter
                            )
                        }
                    }?.let {
                        it.second.getPath()?.takeIf { hasJarEntry(it, sourceSetName) == true }
                    }
            }
        } else null
    }

    private fun resolveVariants(
        module: Module,
        settings: Settings,
        platform: ResolutionPlatform,
    ): List<Variant> {
        val initiallyFilteredVariants = module
            .variants
            .filter { capabilityMatches(it) }
            .filter { nativeTargetMatches(it, platform) }
            .filter { wasmJsTargetMatches(it, platform) }
            .filter { categoryMatches(it) }

        val validVariants = initiallyFilteredVariants
            .filterWithFallbackPlatform(platform)
            .filterWithFallbackJvmEnvironment(platform)
            .filterWithFallbackScope(settings.scope)
            .filterMultipleVariantsByUnusedAttributes()
            .filterWellKnowSpecialLibraries(this.group, this.module)

        return validVariants
    }

    private fun resolveBomVariants(
        module: Module,
        settings: Settings,
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
                && capabilities.contains(
            Capability(
                "com.google.collections",
                "google-collections",
                version.orUnspecified()
            )
        )
                && capabilities.contains(toCapability())
                && getAttributeValue(JvmEnvironment) == when (version?.substringAfterLast('-')) {
            "android" -> JvmEnvironment.Android
            "jre" -> JvmEnvironment.StandardJvm
            else -> null
        }

    private fun isGuava() = group == "com.google.guava" && module == "guava"

    private fun MavenDependency.toCapability() = Capability(group, module, version.orUnspecified())

    private fun nativeTargetMatches(variant: Variant, platform: ResolutionPlatform) =
        !variant.hasKotlinPlatformType(PlatformType.NATIVE)
                || variant.hasNoAttribute(KotlinNativeTarget)
                || variant.hasKotlinNativeTarget(platform)

    private fun wasmJsTargetMatches(variant: Variant, platform: ResolutionPlatform) =
        !variant.hasKotlinPlatformType(PlatformType.WASM)
                || variant.hasNoAttribute(KotlinWasmTarget)
                || variant.hasKotlinWasmTarget(platform)

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

    /**
     * These libraries are applicable in the KMP context but don't have metadata with the set of supported platforms
     * as they're expected to always support everything.
     *
     * Thus, they can be erroneously treated as JVM-only, which isn't the case.
     */
    private fun isSpecialKmpLibrary(): Boolean = isKotlinTestAnnotationsCommon() || isKotlinStdlibCommon()

    private suspend fun resolveUsingPom(
        text: String,
        context: Context,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter,
    ) {
        val project = resolvePom(text, context, level, diagnosticsReporter) ?: return

        if (isBom) {
            dependencyConstraints = project.resolveDependenciesConstraints(context)
        } else {
            (project.dependencies?.dependencies ?: listOf()).filter {
                context.settings.scope.matches(it)
            }.filterNot {
                shouldIgnoreDependency(it.groupId, it.artifactId, context)
            }.filter {
                it.optional != true
            }.map {
                context.createOrReuseDependency(it.groupId, it.artifactId, it.version, false)
            }.let {
                children = it
            }
        }

        packaging = project.packaging ?: "jar"
        state = level.state

        if (packaging == "jar" && !isSpecialKmpLibrary()) {
            val nonJvmPlatforms =
                context.settings.platforms.filter { it != ResolutionPlatform.JVM && it != ResolutionPlatform.ANDROID }
            // JAR packed dependencies without metadata shouldn't be used for non-JVM platforms
            if (nonJvmPlatforms.isNotEmpty()) {
                diagnosticsReporter.addMessage(
                    PlatformsAreNotSupported(
                        this,
                        setOf(ResolutionPlatform.ANDROID, ResolutionPlatform.JVM)
                    )
                )
            }
        }

        // We have used a pom file for resolving dependency, => lower severity of module-metadata-related issues.
        if (moduleFile.diagnosticsReporter.hasErrors()) {
            moduleFile.diagnosticsReporter.suppress { suppressedMessages ->
                PomResolvedWithMetadataErrors(coordinates, suppressedMessages)
            }
        }
    }

    private suspend fun resolvePom(
        text: String, context: Context, level: ResolutionLevel, diagnosticsReporter: DiagnosticReporter,
    ): Project? {
        return try {
            parsePom(text).resolve(context, level, diagnosticsReporter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = UnableToParsePom.asMessage(
                pom,
                extra = DependencyResolutionBundle.message("extra.exception", e),
                exception = e,
            )
            logger.warn(message.message, e)
            diagnosticsReporter.addMessage(message)
            return null
        }
    }

    private fun parsePom(text: String): Project = text.sanitizePom().parsePom()

    private fun String.sanitizePom(): String = let {
        if (group == "org.codehaus.plexus" && module == "plexus") replace("&oslash;", "ø") else it
    }

    private suspend fun resolveDependenciesConstraintsUsingPom(
        text: String, context: Context, level: ResolutionLevel, diagnosticsReporter: DiagnosticReporter
    ): List<MavenDependencyConstraintImpl> = resolvePom(text, context, level, diagnosticsReporter)
        ?.resolveDependenciesConstraints(context)
        ?: emptyList()

    private fun Project.resolveDependenciesConstraints(context: Context): List<MavenDependencyConstraintImpl> {
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
        origin: Project = this,
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
                parsePom(text).resolve(context, resolutionLevel, diagnosticsReporter, depth + 1, origin)
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
            ?.map { it.expandTemplates(project) } // expanding properties used in groupId/artifactId
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
     * If several dependencies are imported, then those are merged into a single [DependencyManagement] object.
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
                        parsePom(text).resolve(context, resolutionLevel, diagnosticsReporter, depth + 1)
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

    private suspend fun DependencyFileImpl.isDownloadedOrDownload(
        level: ResolutionLevel,
        context: Context,
        diagnosticsReporter: DiagnosticReporter,
    ) =
        isDownloadedWithVerification(level, context.settings, diagnosticsReporter)
                || level == ResolutionLevel.NETWORK && download(context, diagnosticsReporter)

    internal val Collection<Variant>.withoutDocumentationAndMetadata: List<Variant>
        get() = filterNot { it.isDocumentationOrMetadata }

    private val Variant.isDocumentationOrMetadata: Boolean
        get() =
            isDocumentation() ||
                    getAttributeValue(Usage) == Usage.KotlinApi
                    && hasKotlinPlatformType(PlatformType.COMMON)

    private val Collection<Variant>.documentationOnly: List<Variant>
        get() = filter { it.isDocumentation() }

    suspend fun downloadDependencies(context: Context, downloadSources: Boolean = false) {
        val withSources = downloadSources || alwaysDownloadSources()

        val allFiles = files(withSources)
        val notDownloaded = allFiles
            .filter {
                (context.settings.platforms.size == 1 // Verification of multiplatform hash is done at the file-producing stage
                        || it.settings[KmpSourceSetName] == null) // (except for artifact with all sources that is not marked with any kmpSourceSet)
                        && !it.isDownloadedWithVerification(settings = context.settings)
            }

        notDownloaded.forEach {
            it.diagnosticsReporter.reset()
            it.download(context, it.diagnosticsReporter)
        }

        allFiles.forEach { it.postProcess(context, withSources, ResolutionLevel.NETWORK, it.diagnosticsReporter) }
    }

    private fun alwaysDownloadSources() = isKotlinStdlib()

    private suspend fun DependencyFileImpl.postProcess(
        context: Context,
        downloadSources: Boolean,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter,
    ) {
        repackageKmpLibrarySources(context, downloadSources, level, diagnosticsReporter)
    }

    private suspend fun DependencyFileImpl.repackageKmpLibrarySources(
        context: Context,
        downloadSources: Boolean,
        level: ResolutionLevel,
        diagnosticsReporter: DiagnosticReporter,
    ) {
        if (context.settings.platforms.size > 1
            && downloadSources
            && this.settings[KmpSourceSetName] == null
            && this.isSourcesDependencyFile()
        ) {
            // repackage KMP library sources.
            val kmpSourcesFile = this
            val sourceSetsSources = context.spanBuilder("repackageKmpLibrarySources")
                .setAttribute("fileName", fileName)
                .use {
                    coroutineScope {
                        sourceSetsFiles
                            .map { sourceSetsFile ->
                                async(Dispatchers.IO) {
                                    context.spanBuilder("toDependencyFile")
                                        .use {
                                            val sourceSetName =
                                                sourceSetsFile.settings[KmpSourceSetName] ?: return@use null
                                            val moduleMetadata =
                                                moduleMetadata ?: error("moduleMetadata wasn't initialized")

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
                            }
                    }.awaitAll()
                        .mapNotNull { it }
                }
            // replace all-in-one sources file with per-sourceSet files
            sourceSetsFiles = sourceSetsFiles - this + sourceSetsSources
        }
    }

    private fun DependencyFileImpl.isSourcesDependencyFile(): Boolean =
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
    val classifier: String? = null,
) {
    override fun toString(): String {
        return "$groupId:$artifactId:${version.orUnspecified()}${if (classifier != null) ":$classifier" else ""}"
    }
}

internal fun AvailableAt.toCoordinates() = MavenCoordinates(group, module, version)

private fun Dependency.isBom(): Boolean = getAttributeValue(Category) == Category.Platform
private fun Variant.isBom(): Boolean = getAttributeValue(Category) == Category.Platform

private val allApplePlatforms = ResolutionPlatform.entries.filter {
    it.name.startsWith("IOS_")
            || it.name.startsWith("MACOS_")
            || it.name.startsWith("TVOS_")
            || it.name.startsWith("WATCHOS_")
}

// todo (AB) : 'strictly' should have special support (we have to take this into account during conflict resolution)
internal fun Version.resolve() = strictly?.resolveSingleVersion()
    ?: requires?.resolveSingleVersion()
    ?: prefers?.resolveSingleVersion()

internal fun Version.asString(): String =
    strictly?.let { "{strictly $strictly} -> $strictly" }
        ?: resolve()
        ?: "null"

internal fun String.resolveSingleVersion() = removeSquareBracketsForSingleValue()
    .reduceInterval()
    .takeIf { !it.isInterval() }

private fun String.isInterval() = startsWith("[") || startsWith("]")

private fun String.removeSquareBracketsForSingleValue() = when {
    startsWith("[") && endsWith("]") && !contains(",") -> substring(1, length - 1)
    else -> this
}

/**
 * Until intervals are fully supported, we try our best to interpret it at least as some viable version
 */
private fun String.reduceInterval() = when {
    startsWith("[") && contains(",") -> substring(1, this.indexOf(","))
    endsWith("]") && contains(",") -> substring(lastIndexOf(",") + 1, length - 1)
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
     * 2. The value of element `<localRepository>` of `~/.m2/settings.xml` if this file exists and an element is set;
     * 3. The value of element `<localRepository>` of `$M2_HOME/conf/settings.xml` (where `$M2_HOME` is the value of the
     *    environment variable with that name) if this file exists and an element is set;
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

fun String?.orUnspecified(): String = this ?: "unspecified"
