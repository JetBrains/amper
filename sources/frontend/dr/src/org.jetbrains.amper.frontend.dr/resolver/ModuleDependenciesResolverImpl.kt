/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.MavenDependencyConstraintNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.SpanBuilderSource
import org.jetbrains.amper.dependency.resolution.UnspecifiedVersionResolver
import org.jetbrains.amper.dependency.resolution.filterGraph
import org.jetbrains.amper.dependency.resolution.originalVersion
import org.jetbrains.amper.dependency.resolution.resolvedVersion
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.dr.resolver.flow.Classpath
import org.jetbrains.amper.frontend.dr.resolver.flow.IdeSync

internal class ModuleDependenciesResolverImpl: ModuleDependenciesResolver {

    override fun AmperModule.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        spanBuilder: SpanBuilderSource?,
    ): ModuleDependencyNodeWithModule {
        val resolutionFlow = when (dependenciesFlowType) {
            is DependenciesFlowType.ClassPathType -> Classpath(dependenciesFlowType)
            is DependenciesFlowType.IdeSyncType -> IdeSync(dependenciesFlowType)
        }

        return resolutionFlow.directDependenciesGraph(this, fileCacheBuilder, spanBuilder)
    }

    override suspend fun DependencyNodeHolder.resolveDependencies(resolutionDepth: ResolutionDepth, resolutionLevel: ResolutionLevel, downloadSources: Boolean) {
        when (resolutionDepth) {
            ResolutionDepth.GRAPH_ONLY -> { /* Do nothing, graph os already given */ }

            ResolutionDepth.GRAPH_WITH_DIRECT_DEPENDENCIES,
            ResolutionDepth.GRAPH_FULL -> {
                val resolver = Resolver()
                resolver.buildGraph(
                    this,
                    level = resolutionLevel,
                    transitive = (resolutionDepth != ResolutionDepth.GRAPH_WITH_DIRECT_DEPENDENCIES),
                    unspecifiedVersionResolver = DirectMavenDependencyUnspecifiedVersionResolver()
                )
                resolver.downloadDependencies(this, downloadSources)
            }
        }
    }

    override suspend fun AmperModule.resolveDependencies(resolutionInput: ResolutionInput): ModuleDependencyNodeWithModule {
        with(resolutionInput) {
            val moduleDependenciesGraph = resolveDependenciesGraph(dependenciesFlowType, fileCacheBuilder, spanBuilder)
            moduleDependenciesGraph.resolveDependencies(resolutionDepth, resolutionLevel, downloadSources)
            return moduleDependenciesGraph
        }
    }

    private fun List<AmperModule>.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        spanBuilder: SpanBuilderSource?,
    ): DependencyNodeHolder {
        val resolutionFlow = when (dependenciesFlowType) {
            is DependenciesFlowType.ClassPathType -> Classpath(dependenciesFlowType)
            is DependenciesFlowType.IdeSyncType -> IdeSync(dependenciesFlowType)
        }

        return resolutionFlow.directDependenciesGraph(this, fileCacheBuilder, spanBuilder)
    }

    override suspend fun List<AmperModule>.resolveDependencies(resolutionInput: ResolutionInput): DependencyNodeHolder {
        with(resolutionInput) {
            val moduleDependenciesGraph = resolveDependenciesGraph(dependenciesFlowType, fileCacheBuilder, resolutionInput.spanBuilder)
            moduleDependenciesGraph.resolveDependencies(resolutionDepth, resolutionLevel, downloadSources)
            return moduleDependenciesGraph
        }
    }

    override fun dependencyInsight(group: String, module: String, node: DependencyNode, resolvedVersionOnly: Boolean): DependencyNode =
        filterGraph(group, module, node, resolvedVersionOnly)

    override suspend fun AmperModule.dependencyInsight(group: String, module: String, resolutionInput: ResolutionInput): DependencyNode {
        val graph = resolveDependencies(resolutionInput)
        return filterGraph(group, module, graph)
    }
}

class DirectMavenDependencyUnspecifiedVersionResolver(): UnspecifiedVersionResolver<MavenDependencyNode> {

    override fun isApplicable(node: MavenDependencyNode): Boolean {
        return node.originalVersion() == null
                && node.parents.any { it is DirectFragmentDependencyNodeHolder }
    }

    override fun resolveVersions(nodes: Set<MavenDependencyNode>): Map<MavenDependencyNode, String> {
        return nodes.mapNotNull { node ->
            if (!isApplicable(node)) {
                null
            } else {
                resolveVersionFromBom(node)?.let { node to it }
            }
        }.toMap()
    }

    /**
     * Resolve an unspecified dependency version from the BOM imported in the same module.
     * Unspecified dependency version should not be taken from transitive dependencies or constraints.
     */
    private fun resolveVersionFromBom(node: MavenDependencyNode): String? {
        val boms = node.parents.filter { it is DirectFragmentDependencyNodeHolder }
            .mapNotNull { it.parents.singleOrNull() as? ModuleDependencyNodeWithModule }
            .map { node ->
                node.children
                    .filterIsInstance<DirectFragmentDependencyNodeHolder>()
                    .map { it.dependencyNode }
                    .filterIsInstance<MavenDependencyNode>()
                    .filter { it.isBom }
            }.flatten()

        boms.forEach { bom ->
            val resolvedVersion = bom.children
                .filterIsInstance<MavenDependencyConstraintNode>()
                .firstOrNull { it.key == node.key }
                ?.originalVersion()

            if (resolvedVersion != null) {
                return resolvedVersion
            }
        }

        return null
    }
}
