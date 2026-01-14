/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver

import io.opentelemetry.api.OpenTelemetry
import kotlinx.serialization.modules.SerializersModuleBuilder
import org.jetbrains.amper.dependency.resolution.DependencyGraph.Companion.toSerializableReference
import org.jetbrains.amper.dependency.resolution.DependencyGraphContext
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolderWithContext
import org.jetbrains.amper.dependency.resolution.DependencyNodeReference
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.GraphSerializableTypesProvider
import org.jetbrains.amper.dependency.resolution.IncrementalCacheUsage
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.MavenDependencyUnspecifiedVersionResolverBase
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.ResolvedGraph
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.RootDependencyNodeWithContext
import org.jetbrains.amper.dependency.resolution.SerializableDependencyNode
import org.jetbrains.amper.dependency.resolution.SerializableDependencyNodeConverter
import org.jetbrains.amper.dependency.resolution.SerializableRootDependencyNode
import org.jetbrains.amper.dependency.resolution.filterGraph
import org.jetbrains.amper.dependency.resolution.spanBuilder
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.dr.resolver.flow.Classpath
import org.jetbrains.amper.frontend.dr.resolver.flow.IdeSync
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger(ModuleDependenciesResolverImpl::class.java)

internal class ModuleDependenciesResolverImpl: ModuleDependenciesResolver {

    override fun AmperModule.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?
    ): ModuleDependencyNodeWithModuleAndContext {
        val resolutionFlow = when (dependenciesFlowType) {
            is DependenciesFlowType.ClassPathType -> Classpath(dependenciesFlowType)
            is DependenciesFlowType.IdeSyncType -> IdeSync(dependenciesFlowType)
        }

        return resolutionFlow.directDependenciesGraph(this, fileCacheBuilder, openTelemetry, incrementalCache)
    }

    override suspend fun DependencyNodeHolderWithContext.resolveDependencies(
        resolutionDepth: ResolutionDepth,
        resolutionLevel: ResolutionLevel,
        downloadSources: Boolean,
        incrementalCacheUsage: IncrementalCacheUsage
    ): ResolvedGraph {
        return context.spanBuilder("DR.graph:resolveDependencies").use {
            when (resolutionDepth) {
                ResolutionDepth.GRAPH_ONLY -> {
                    /* Do nothing, graph is already given */
                    ResolvedGraph(this@resolveDependencies, null)
                }

                ResolutionDepth.GRAPH_WITH_DIRECT_DEPENDENCIES,
                ResolutionDepth.GRAPH_FULL,
                    -> {
                    val resolvedGraph = Resolver().resolveDependencies(
                        root = this@resolveDependencies,
                        resolutionLevel,
                        downloadSources,
                        resolutionDepth != ResolutionDepth.GRAPH_WITH_DIRECT_DEPENDENCIES,
                        incrementalCacheUsage = incrementalCacheUsage,
                        DirectMavenDependencyUnspecifiedVersionResolver(),
                        postProcessGraph = {
                            // Merge the input graph (that has PSI references) with the deserialized one
                            it.fillNotation(this@resolveDependencies)
                        }
                    )
                    resolvedGraph
                }
            }
        }
    }

    private fun SerializableDependencyNode.fillNotation(sourceNode: DependencyNodeHolderWithContext) {
        val sourceDirectDeps = sourceNode.children.groupBy { it.key }
        this.children.forEach { node ->
            when (node) {
                is SerializableDirectFragmentDependencyNodeHolder -> {
                    val sourceNode = sourceDirectDeps[node.key].resolveCorrespondingSourceNode<DirectFragmentDependencyNodeHolderWithContext>(node) {
                        node.dependencyNode.getOriginalMavenCoordinates() == notation.coordinates
                    }
                    node.notation = sourceNode.notation
                }
                is SerializableModuleDependencyNodeWithModule -> {
                    val sourceNode = sourceDirectDeps[node.key].resolveCorrespondingSourceNode<ModuleDependencyNodeWithModuleAndContext>(node)
                    node.notation = sourceNode.notation
                    node.fillNotation(sourceNode)
                }
                is SerializableRootDependencyNode -> {
                    val sourceNode = sourceDirectDeps[node.key].resolveCorrespondingSourceNode<RootDependencyNodeWithContext>(node)
                    node.fillNotation(sourceNode)
                }
            }
        }
    }

    private inline fun <reified T: DependencyNode> List<DependencyNode>?.resolveCorrespondingSourceNode(
        node: SerializableDependencyNode,
        additionalMatch: T.() -> Boolean = { true }
    ): T {
        if (this == null || this.isEmpty())
            error("Deserialized node with key ${node.key} has no corresponding input node")

        this.forEach {
            (it as? T) ?: error(
                "Deserialized node corresponds to unexpected input node of type " +
                        "${this::class.simpleName} while ${node::class.simpleName} is expected"
            )
            if (it.additionalMatch()) return it
        }

        return (this.first() as T)
    }

    override suspend fun AmperModule.resolveDependencies(resolutionInput: ResolutionInput): ModuleDependencyNode {
        with(resolutionInput) {
            val moduleDependenciesGraph = resolveDependenciesGraph(dependenciesFlowType, fileCacheBuilder, openTelemetry, incrementalCache)
            val resolvedGraph = moduleDependenciesGraph.resolveDependencies(resolutionDepth, resolutionLevel, downloadSources)
            return resolvedGraph.root as ModuleDependencyNode
        }
    }

    override fun List<AmperModule>.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        openTelemetry: OpenTelemetry?,
        incrementalCache: IncrementalCache?
    ): RootDependencyNodeWithContext {
        val resolutionFlow = when (dependenciesFlowType) {
            is DependenciesFlowType.ClassPathType -> Classpath(dependenciesFlowType)
            is DependenciesFlowType.IdeSyncType -> IdeSync(dependenciesFlowType)
        }

        return resolutionFlow.directDependenciesGraph(this, fileCacheBuilder, openTelemetry, incrementalCache)
    }

    override suspend fun List<AmperModule>.resolveDependencies(resolutionInput: ResolutionInput): DependencyNode {
        return with(resolutionInput) {
            resolutionInput.openTelemetry.spanBuilder("DR: Resolving dependencies for the list of modules").use {
                val moduleDependenciesGraph = resolveDependenciesGraph(dependenciesFlowType, fileCacheBuilder, openTelemetry, incrementalCache)
                val resolvedGraph = moduleDependenciesGraph.resolveDependencies(resolutionDepth, resolutionLevel, downloadSources, incrementalCacheUsage)
                resolvedGraph.root
            }
        }
    }

    override fun dependencyInsight(group: String, module: String, node: DependencyNode, resolvedVersionOnly: Boolean): DependencyNode =
        filterGraph(group, module, node, resolvedVersionOnly)

    override suspend fun AmperModule.dependencyInsight(group: String, module: String, resolutionInput: ResolutionInput): DependencyNode {
        val graph = resolveDependencies(resolutionInput)
        return filterGraph(group, module, graph)
    }
}

internal class AmperDrSerializableTypesProvider: GraphSerializableTypesProvider {
    override fun getSerializableConverters() =
        ModuleDependencyNodeWithModuleConverter.converters() +
                DirectFragmentDependencyNodeConverter.converters()

    override fun SerializersModuleBuilder.registerPolymorphic() {
        moduleForDependencyNodePlainHierarchy()
        moduleForDependencyNodeHierarchy()
    }

    fun SerializersModuleBuilder.moduleForDependencyNodePlainHierarchy() =
        moduleForDependencyNodeHierarchy(SerializableDependencyNode::class as KClass<DependencyNode>)

    fun SerializersModuleBuilder.moduleForDependencyNodeHierarchy() =
        moduleForDependencyNodeHierarchy(DependencyNode::class)

    fun SerializersModuleBuilder.moduleForDependencyNodeHierarchy(kClass: KClass<DependencyNode>) {
        polymorphic(kClass,SerializableModuleDependencyNodeWithModule::class, SerializableModuleDependencyNodeWithModule.serializer())
        polymorphic(kClass,SerializableDirectFragmentDependencyNodeHolder::class, SerializableDirectFragmentDependencyNodeHolder.serializer())
    }
}

private sealed class ModuleDependencyNodeWithModuleConverter<T: ModuleDependencyNode>: SerializableDependencyNodeConverter<T, SerializableModuleDependencyNodeWithModule>  {
    object Input: ModuleDependencyNodeWithModuleConverter<ModuleDependencyNodeWithModuleAndContext>() {
        override fun applicableTo() = ModuleDependencyNodeWithModuleAndContext::class
    }
    object Plain: ModuleDependencyNodeWithModuleConverter<SerializableModuleDependencyNodeWithModule>() {
        override fun applicableTo() = SerializableModuleDependencyNodeWithModule::class
    }

    override fun toEmptyNodePlain(node: T, graphContext: DependencyGraphContext): SerializableModuleDependencyNodeWithModule =
        SerializableModuleDependencyNodeWithModule(node.moduleName, node.graphEntryName, graphContext = graphContext)

    companion object {
        fun converters()= listOf(Input, Plain)
    }
}

private sealed class DirectFragmentDependencyNodeConverter<T: DirectFragmentDependencyNode>
    : SerializableDependencyNodeConverter<T, SerializableDirectFragmentDependencyNodeHolder>
{
    object Input: DirectFragmentDependencyNodeConverter<DirectFragmentDependencyNodeHolderWithContext>() {
        override fun applicableTo() = DirectFragmentDependencyNodeHolderWithContext::class
    }
    object Plain: DirectFragmentDependencyNodeConverter<SerializableDirectFragmentDependencyNodeHolder>() {
        override fun applicableTo() = SerializableDirectFragmentDependencyNodeHolder::class
    }

    override fun toEmptyNodePlain(node: T, graphContext: DependencyGraphContext): SerializableDirectFragmentDependencyNodeHolder =
        SerializableDirectFragmentDependencyNodeHolder(
            node.fragmentName, node.graphEntryName, node.messages, graphContext = graphContext)

    override fun fillEmptyNodePlain(nodePlain: SerializableDirectFragmentDependencyNodeHolder, node: T, graphContext: DependencyGraphContext, nodeReference: DependencyNodeReference?) {
        super.fillEmptyNodePlain(nodePlain, node, graphContext, nodeReference)
        nodePlain.dependencyNodeRef =
            graphContext.getDependencyNodeReferenceAndSetParent(node.dependencyNode, nodeReference)
                ?: node.dependencyNode.toSerializableReference(graphContext,nodeReference)
    }

    companion object {
        fun converters() = listOf(Input, Plain)
    }
}

class DirectMavenDependencyUnspecifiedVersionResolver: MavenDependencyUnspecifiedVersionResolverBase() {

    override fun getBomNodes(node: MavenDependencyNodeWithContext): List<MavenDependencyNode> {
        val directDependencyParents = node.directDependencyParents()
        val boms = if (directDependencyParents.isNotEmpty()) {
            // Using BOM from the same module for resolving direct module dependencies
            directDependencyParents
                .mapNotNull { it.parents.singleOrNull() as? ModuleDependencyNode }
                .map { parent ->
                    parent.children
                        .filterIsInstance<DirectFragmentDependencyNode>()
                        .map { it.dependencyNode }
                        .filterIsInstance<MavenDependencyNode>()
                        .filter { it.isBom }
                }.flatten()
        } else {
            super.getBomNodes(node)
        }

        return boms
    }

    /**
     * @return list of [DirectFragmentDependencyNode]s that depend on this maven libary (either directly or transitevly)
     */
    private fun MavenDependencyNodeWithContext.directDependencyParents(): List<DirectFragmentDependencyNode> {
        return when {
            // Direct dependency
            parents.any { it is DirectFragmentDependencyNode } -> parents.filterIsInstance<DirectFragmentDependencyNode>()
            // Transitive dependency,
            // find all direct dependencies this transitive one is referenced by and use those for BOM resolution
            else -> fragmentDependencies
        }
    }
}
