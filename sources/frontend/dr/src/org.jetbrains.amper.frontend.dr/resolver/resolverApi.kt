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
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolderWithContext
import org.jetbrains.amper.dependency.resolution.DependencyNodeReference
import org.jetbrains.amper.dependency.resolution.DependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.IncrementalCacheUsage
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.SerializableDependencyNodeHolderBase
import org.jetbrains.amper.dependency.resolution.currentGraphContext
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
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
import kotlin.error

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
    ): ModuleDependencyNodeWithModuleAndContext

    fun List<AmperModule>.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?
    ): RootDependencyNodeWithContext

    /**
     * @return dependency node representing the root of the resolved graph
     */
    suspend fun DependencyNodeHolderWithContext.resolveDependencies(
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

abstract class DependencyNodeHolderWithNotationAndContext(
    graphEntryName: String,
    children: List<DependencyNodeWithContext>,
    templateContext: Context,
    open val notation: Notation? = null,
    parentNodes: Set<DependencyNodeWithContext> = emptySet(),
) : DependencyNodeHolderWithContext(graphEntryName, children, templateContext, parentNodes)

interface ModuleDependencyNode: DependencyNodeHolder {
    val moduleName: String
    val notation: LocalModuleDependency?
}

class ModuleDependencyNodeWithModuleAndContext(
    val module: AmperModule,
    graphEntryName: String,
    children: List<DependencyNodeWithContext>,
    templateContext: Context,
    override val notation: LocalModuleDependency? = null,
    parentNodes: Set<DependencyNodeWithContext> = emptySet(),
) : ModuleDependencyNode,
    DependencyNodeHolderWithNotationAndContext(
        graphEntryName, children, templateContext, notation, parentNodes = parentNodes)
{
    override val moduleName = module.userReadableName

    override val cacheEntryKey: CacheEntryKey
        get() = CacheEntryKey.fromString(module.uniqueModuleKey())
}

@Serializable
internal class SerializableModuleDependencyNodeWithModule internal constructor(
    override val moduleName: String,
    override val graphEntryName: String,
    override val parentsRefs: MutableSet<DependencyNodeReference> = mutableSetOf(),
    override val childrenRefs: List<DependencyNodeReference> = mutableListOf(),
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext(),
): ModuleDependencyNode, SerializableDependencyNodeHolderBase(graphContext) {

    override val messages: List<Message> = listOf()

    @Transient
    override var notation: LocalModuleDependency? = null

}

interface DirectFragmentDependencyNode: DependencyNodeHolder {
    val fragmentName: String
    val dependencyNode: DependencyNode
    val notation: MavenDependencyBase
}

class DirectFragmentDependencyNodeHolderWithContext(
    override val dependencyNode: MavenDependencyNodeWithContext,
    val fragment: Fragment,
    templateContext: Context,
    override val notation: MavenDependencyBase,
    parentNodes: Set<DependencyNodeWithContext> = emptySet(),
    override val messages: List<Message> = emptyList(),
) : DirectFragmentDependencyNode,
    DependencyNodeHolderWithNotationAndContext(
        graphEntryName = "${fragment.module.userReadableName}:${fragment.name}:${dependencyNode}${traceInfo(notation)}",
        listOf(dependencyNode), templateContext, notation, parentNodes = parentNodes
) {
    override val fragmentName: String = fragment.name

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
internal class SerializableDirectFragmentDependencyNodeHolder internal constructor(
    override val fragmentName: String,
    override val graphEntryName: String,
    override val messages: List<Message>,
    override val parentsRefs: MutableSet<DependencyNodeReference> = mutableSetOf(),
    override val childrenRefs: List<DependencyNodeReference> = mutableListOf(),
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext()
): DirectFragmentDependencyNode, SerializableDependencyNodeHolderBase(graphContext) {

    lateinit var dependencyNodeRef: DependencyNodeReference

    override val dependencyNode: MavenDependencyNode by lazy {
        dependencyNodeRef.toNodePlain(graphContext)
            .let {
                it as? MavenDependencyNode
                    ?: error("Unexpected dependency node type [${it::class.simpleName}]")
            }
    }

    @Transient
    override lateinit var notation: MavenDependencyBase
}