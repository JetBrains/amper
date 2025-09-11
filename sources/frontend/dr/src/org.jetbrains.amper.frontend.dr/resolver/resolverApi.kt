/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyGraphContext
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolderPlain
import org.jetbrains.amper.dependency.resolution.DependencyNodePlain
import org.jetbrains.amper.dependency.resolution.DependencyNodeReference
import org.jetbrains.amper.dependency.resolution.DependencyNodeWithResolutionContext
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.Key
import org.jetbrains.amper.dependency.resolution.NoopSpanBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeInput
import org.jetbrains.amper.dependency.resolution.SpanBuilderSource
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
    val skipIncrementalCache: Boolean= false,
    val fileCacheBuilder: FileCacheBuilder.() -> Unit,
    // todo (AB) : Replace it with OpenTelemetry (it could provide tracer as well as other useful stuff: meters)
    val spanBuilder: SpanBuilderSource = { NoopSpanBuilder.create() },
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
        spanBuilder: SpanBuilderSource = { NoopSpanBuilder.create() },
    ): ModuleDependencyNodeWithModule

    fun List<AmperModule>.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        spanBuilder: SpanBuilderSource = { NoopSpanBuilder.create() },
    ): RootDependencyNodeInput

    /**
     * @return dependency node representing the root of the resolved graph
     */
    suspend fun DependencyNodeHolder.resolveDependencies(
        resolutionDepth: ResolutionDepth,
        resolutionLevel: ResolutionLevel = ResolutionLevel.NETWORK,
        downloadSources: Boolean = false,
        skipIncrementalCache: Boolean= false,
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
    name: String,
    children: List<DependencyNodeWithResolutionContext>,
    templateContext: Context,
    open val notation: Notation? = null,
    parentNodes: List<DependencyNodeWithResolutionContext> = emptyList(),
) : DependencyNodeHolder(name, children, templateContext, parentNodes)

interface ModuleDependencyNode: DependencyNode {
    val moduleName: String
    val notation: LocalModuleDependency?
}

class ModuleDependencyNodeWithModule(
    val module: AmperModule,
    name: String,
    children: List<DependencyNodeWithResolutionContext>,
    templateContext: Context,
    override val notation: LocalModuleDependency? = null,
    parentNodes: List<DependencyNodeWithResolutionContext> = emptyList(),
) : DependencyNodeHolderWithNotation(name, children, templateContext, notation, parentNodes = parentNodes),
    ModuleDependencyNode
{
    override val moduleName = module.userReadableName

    override fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        ModuleDependencyNodeWithModulePlain(module.userReadableName, name, graphContext = graphContext)
}

@Serializable
class ModuleDependencyNodeWithModulePlain internal constructor(
    override val moduleName: String,
    val name: String,
    override val parentsRefs: List<DependencyNodeReference> = mutableListOf(),
    override val childrenRefs: List<DependencyNodeReference> = mutableListOf(),
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext(),
): DependencyNodeHolderPlain, ModuleDependencyNode {
    override val parents: MutableList<DependencyNode> by lazy { parentsRefs.map { it.toNodePlain(graphContext) }.toMutableList() }
    override val children: List<DependencyNode> by lazy { childrenRefs.map { it.toNodePlain(graphContext) } }

    @Transient
    override val key: Key<*> = Key<DependencyNodeHolder>(name)
    override val messages: List<Message> = listOf()

    @Transient
    override var notation: LocalModuleDependency? = null

    override fun toString() = name
}

interface DirectFragmentDependencyNode: DependencyNode {
    val fragmentName: String
    val dependencyNode: DependencyNode
    val notation: MavenDependencyBase
}

class DirectFragmentDependencyNodeHolder(
    override val dependencyNode: DependencyNodeWithResolutionContext,
    val fragment: Fragment,
    templateContext: Context,
    override val notation: MavenDependencyBase,
    parentNodes: List<DependencyNodeWithResolutionContext> = emptyList(),
    override val messages: List<Message> = emptyList(),
) : DirectFragmentDependencyNode, DependencyNodeHolderWithNotation(
    name = "${fragment.module.userReadableName}:${fragment.name}:${dependencyNode}${traceInfo(notation)}",
    listOf(dependencyNode), templateContext, notation, parentNodes = parentNodes
) {
    override val fragmentName: String = fragment.name

    override fun toEmptyNodePlain(graphContext: DependencyGraphContext): DependencyNodePlain =
        DirectFragmentDependencyNodeHolderPlain(
            fragment.name, name, notation.coordinates.value,
            messages, graphContext = graphContext
        )

    override fun fillEmptyNodePlain(nodePlain: DependencyNodePlain, graphContext: DependencyGraphContext) {
        super.fillEmptyNodePlain(nodePlain, graphContext)
        (nodePlain as DirectFragmentDependencyNodeHolderPlain).dependencyNodeRef =
            graphContext.getDependencyNodeReference(dependencyNode)
                ?: dependencyNode.toSerializableReference(graphContext)
    }
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
class DirectFragmentDependencyNodeHolderPlain internal constructor(
    override val fragmentName: String,
    val name: String,
    val notationCoordinates: String,
    override val messages: List<Message>,
    override val parentsRefs: List<DependencyNodeReference> = mutableListOf(),
    override val childrenRefs: List<DependencyNodeReference> = mutableListOf(),
    @Transient
    private val graphContext: DependencyGraphContext = currentGraphContext()

): DependencyNodeHolderPlain, DirectFragmentDependencyNode {
    override val parents: MutableList<DependencyNode> by lazy { parentsRefs.map { it.toNodePlain(graphContext) }.toMutableList() }
    override val children: List<DependencyNode> by lazy { childrenRefs.map { it.toNodePlain(graphContext) } }

    @Transient
    override val key: Key<*> = Key<DependencyNodeHolder>(name)

    lateinit var dependencyNodeRef: DependencyNodeReference

    @Transient
    override lateinit var notation: MavenDependencyBase

    override val dependencyNode: DependencyNode by lazy { dependencyNodeRef.toNodePlain(graphContext) }

    override fun toString() = name
}

