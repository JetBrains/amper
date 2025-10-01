/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver

import io.opentelemetry.api.OpenTelemetry
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.amper.dependency.resolution.CacheEntryKey
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyGraphContext
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolderImpl
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolderPlainBase
import org.jetbrains.amper.dependency.resolution.DependencyNodePlain
import org.jetbrains.amper.dependency.resolution.DependencyNodePlainBase
import org.jetbrains.amper.dependency.resolution.DependencyNodeReference
import org.jetbrains.amper.dependency.resolution.DependencyNodeWithResolutionContext
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.IncrementalCacheUsage
import org.jetbrains.amper.dependency.resolution.Key
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeInput
import org.jetbrains.amper.dependency.resolution.currentGraphContext
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.toSerializableReference
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.ResolvedReferenceTrace
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.incrementalcache.IncrementalCache

val moduleDependenciesResolver: ModuleDependenciesResolver = ModuleDependenciesResolverImpl()

enum class ResolutionDepth {
    GRAPH_ONLY,
    GRAPH_WITH_DIRECT_DEPENDENCIES,
    GRAPH_FULL
}

data class ResolutionInput(
    val dependenciesFlowType: DependenciesFlowType,
    val resolutionDepth: ResolutionDepth,
    val resolutionLevel: ResolutionLevel = ResolutionLevel.NETWORK,
    val downloadSources: Boolean = false,
    val incrementalCacheUsage: IncrementalCacheUsage = IncrementalCacheUsage.USE,
    val fileCacheBuilder: FileCacheBuilder.() -> Unit,
    val openTelemetry: OpenTelemetry? = null,
    val incrementalCache: IncrementalCache? = null,
)

sealed interface DependenciesFlowType {
    data class ClassPathType(
        val scope: ResolutionScope,
        val platforms: Set<ResolutionPlatform>,
        val isTest: Boolean,
        val includeNonExportedNative: Boolean = true
    ) : DependenciesFlowType

    data class IdeSyncType(val aom: Model) : DependenciesFlowType
}

interface ModuleDependenciesResolver {
    fun AmperModule.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?
    ): ModuleDependencyNodeWithModule

    fun List<AmperModule>.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?
    ): RootDependencyNodeInput

    /**
     * @return dependency node representing the root of the resolved graph
     */
    suspend fun DependencyNodeHolderImpl.resolveDependencies(
        resolutionDepth: ResolutionDepth,
        resolutionLevel: ResolutionLevel = ResolutionLevel.NETWORK,
        downloadSources: Boolean = false,
        incrementalCacheUsage: IncrementalCacheUsage = IncrementalCacheUsage.SKIP,
    ): DependencyNode

    suspend fun AmperModule.resolveDependencies(resolutionInput: ResolutionInput): ModuleDependencyNode

    /**
     * Returned filtered dependencies graph,
     * containing paths from the root to the maven dependency node corresponding to the given coordinates (group and module)
     * and having the version equal to the actual resolved version of this dependency in the graph.
     * If the resolved dependency version is enforced by constraint, then the path to that constraint is presented
     * in a returned graph together with paths to all versions of this dependency.
     *
     * Every node of the returned graph is of the type [DependencyNode] holding the corresponding node from the original graph inside.
     */
    suspend fun AmperModule.dependencyInsight(
        group: String,
        module: String,
        resolutionInput: ResolutionInput
    ): DependencyNode

    fun dependencyInsight(group: String, module: String, node: DependencyNode, resolvedVersionOnly: Boolean = false): DependencyNode

    suspend fun List<AmperModule>.resolveDependencies(resolutionInput: ResolutionInput): DependencyNode
}

abstract class DependencyNodeHolderWithNotation(
    graphEntryName: String,
    children: List<DependencyNodeWithResolutionContext>,
    templateContext: Context,
    open val notation: Notation? = null,
    parentNodes: Set<DependencyNodeWithResolutionContext> = emptySet(),
) : DependencyNodeHolderImpl(graphEntryName, children, templateContext, parentNodes)

interface ModuleDependencyNode: DependencyNodeHolder {
    val moduleName: String
    val notation: LocalModuleDependency?

    override fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        ModuleDependencyNodeWithModulePlain(moduleName, graphEntryName, graphContext = graphContext)
}

class ModuleDependencyNodeWithModule(
    val module: AmperModule,
    graphEntryName: String,
    children: List<DependencyNodeWithResolutionContext>,
    templateContext: Context,
    override val notation: LocalModuleDependency? = null,
    parentNodes: Set<DependencyNodeWithResolutionContext> = emptySet(),
) : ModuleDependencyNode,
    DependencyNodeHolderWithNotation(
        graphEntryName, children, templateContext, notation, parentNodes = parentNodes)
{
    override val moduleName = module.userReadableName

    override val cacheEntryKey: CacheEntryKey
        get() = CacheEntryKey.fromString(module.uniqueModuleKey())
}

@Serializable
internal class ModuleDependencyNodeWithModulePlain internal constructor(
    override val moduleName: String,
    override val graphEntryName: String,
    override val parentsRefs: MutableSet<DependencyNodeReference> = mutableSetOf(),
    override val childrenRefs: List<DependencyNodeReference> = mutableListOf(),
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext(),
): ModuleDependencyNode, DependencyNodeHolderPlainBase(graphContext) {

    override val messages: List<Message> = listOf()

    @Transient
    override var notation: LocalModuleDependency? = null

}

interface DirectFragmentDependencyNode: DependencyNodeHolder {
    val fragmentName: String
    val dependencyNode: DependencyNode
    val notationCoordinates: String
    val notation: MavenDependencyBase

    override fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        DirectFragmentDependencyNodeHolderPlain(
            fragmentName, graphEntryName, notationCoordinates, messages, graphContext = graphContext)

    override fun fillEmptyNodePlain(nodePlain: DependencyNodePlain, graphContext: DependencyGraphContext, nodeReference: DependencyNodeReference?) {
        super.fillEmptyNodePlain(nodePlain, graphContext, nodeReference)
        (nodePlain as DirectFragmentDependencyNodeHolderPlain).dependencyNodeRef =
            graphContext.getDependencyNodeReferenceAndSetParent(dependencyNode, nodeReference)
                ?: dependencyNode.toSerializableReference(graphContext,nodeReference)
    }
}

class DirectFragmentDependencyNodeHolder(
    override val dependencyNode: DependencyNodeWithResolutionContext,
    val fragment: Fragment,
    templateContext: Context,
    override val notation: MavenDependencyBase,
    parentNodes: Set<DependencyNodeWithResolutionContext> = emptySet(),
    override val messages: List<Message> = emptyList(),
) : DirectFragmentDependencyNode,
    DependencyNodeHolderWithNotation(
        graphEntryName = "${fragment.module.userReadableName}:${fragment.name}:${dependencyNode}${traceInfo(notation)}",
        listOf(dependencyNode), templateContext, notation, parentNodes = parentNodes
) {
    override val fragmentName: String = fragment.name
    override val notationCoordinates: String = notation.coordinates.value

    override val cacheEntryKey: CacheEntryKey
        get() = CacheEntryKey.CompositeCacheEntryKey(listOf(
            fragment.module.uniqueModuleKey(),
            fragment.name,
        ))
}

private fun traceInfo(notation: Notation): String {
    val sourceInfo = when (val trace = notation.trace) {
        is DefaultTrace -> "implicit"
        is TransformedValueTrace -> "implicit (${trace.description})"
        is ResolvedReferenceTrace -> trace.description
        is BuiltinCatalogTrace -> null // should never happen for dependency Notation
        // TODO maybe write something if the dependency comes from a template?
        is PsiTrace -> null // we don't want to clutter the output for 'regular' dependencies declared in files
    }
    return sourceInfo?.let { ", $it" } ?: ""
}

@Serializable
internal class DirectFragmentDependencyNodeHolderPlain internal constructor(
    override val fragmentName: String,
    override val graphEntryName: String,
    override val notationCoordinates: String,
    override val messages: List<Message>,
    override val parentsRefs: MutableSet<DependencyNodeReference> = mutableSetOf(),
    override val childrenRefs: List<DependencyNodeReference> = mutableListOf(),
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext()
): DirectFragmentDependencyNode, DependencyNodeHolderPlainBase(graphContext) {

    lateinit var dependencyNodeRef: DependencyNodeReference

    override val dependencyNode: DependencyNode by lazy { dependencyNodeRef.toNodePlain(graphContext) }

    @Transient
    override lateinit var notation: MavenDependencyBase
}

internal interface UnresolvedMavenDependencyNode : DependencyNode {
    val coordinates: String
    override val key: Key<*> get() = Key<UnresolvedMavenDependencyNode>(coordinates)
    override val graphEntryName: String get() = "$coordinates, unresolved"

    fun key() = Key<UnresolvedMavenDependencyNode>(coordinates)

    override fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        UnresolvedMavenDependencyNodePlain(coordinates, graphContext = graphContext)
}

@Serializable
internal class UnresolvedMavenDependencyNodePlain internal constructor(
    override val coordinates: String,
    override val parentsRefs: MutableSet<DependencyNodeReference> = mutableSetOf(),
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext()
): UnresolvedMavenDependencyNode, DependencyNodePlainBase(graphContext) {
    override val childrenRefs: List<DependencyNodeReference> = emptyList()
    override val messages: List<Message> = emptyList()
}

internal class UnresolvedMavenDependencyNodeImpl(
    override val coordinates: String,
    templateContext: Context,
    parentNodes: Set<DependencyNodeWithResolutionContext> = emptySet(),
) : UnresolvedMavenDependencyNode, DependencyNodeWithResolutionContext {
    override val context = templateContext.copyWithNewNodeCache(parentNodes)
    override val children: List<DependencyNodeWithResolutionContext> = emptyList()
    override val messages: List<Message> = emptyList()
    override suspend fun resolveChildren(level: ResolutionLevel, transitive: Boolean) {}
    override suspend fun downloadDependencies(downloadSources: Boolean) {}
    override fun toString(): String = graphEntryName
    override val cacheEntryKey: CacheEntryKey
        get() = CacheEntryKey.fromString(coordinates)
}