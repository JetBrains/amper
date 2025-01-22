/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.Resolver
import org.jetbrains.amper.dependency.resolution.filterGraph
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.dr.resolver.flow.Classpath
import org.jetbrains.amper.frontend.dr.resolver.flow.IdeSync
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ModuleDependenciesResolverImpl::class.java)

internal class ModuleDependenciesResolverImpl: ModuleDependenciesResolver {

    override fun AmperModule.resolveDependenciesGraph(dependenciesFlowType: DependenciesFlowType, fileCacheBuilder: FileCacheBuilder.() -> Unit): ModuleDependencyNodeWithModule {
        val resolutionFlow = when (dependenciesFlowType) {
            is DependenciesFlowType.ClassPathType -> Classpath(dependenciesFlowType)
            is DependenciesFlowType.IdeSyncType -> IdeSync(dependenciesFlowType)
        }

        return resolutionFlow.directDependenciesGraph(this, fileCacheBuilder)
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
                    transitive = (resolutionDepth != ResolutionDepth.GRAPH_WITH_DIRECT_DEPENDENCIES)
                )
                resolver.downloadDependencies(this, downloadSources)
            }
        }
    }

    override suspend fun AmperModule.resolveDependencies(resolutionInput: ResolutionInput): ModuleDependencyNodeWithModule {
        with(resolutionInput) {
            val moduleDependenciesGraph = resolveDependenciesGraph(dependenciesFlowType, fileCacheBuilder)
            moduleDependenciesGraph.resolveDependencies(resolutionDepth, resolutionLevel, downloadSources)
            return moduleDependenciesGraph
        }
    }

    private fun List<AmperModule>.resolveDependenciesGraph(dependenciesFlowType: DependenciesFlowType, fileCacheBuilder: FileCacheBuilder.() -> Unit): DependencyNodeHolder {
        val resolutionFlow = when (dependenciesFlowType) {
            is DependenciesFlowType.ClassPathType -> Classpath(dependenciesFlowType)
            is DependenciesFlowType.IdeSyncType -> IdeSync(dependenciesFlowType)
        }

        return resolutionFlow.directDependenciesGraph(this, fileCacheBuilder)
    }

    override suspend fun List<AmperModule>.resolveDependencies(resolutionInput: ResolutionInput): DependencyNodeHolder {
        with(resolutionInput) {
            val moduleDependenciesGraph = resolveDependenciesGraph(dependenciesFlowType, fileCacheBuilder)
            moduleDependenciesGraph.resolveDependencies(resolutionDepth, resolutionLevel, downloadSources)
            return moduleDependenciesGraph
        }
    }

    override fun dependencyInsight(group: String, module: String, graph: DependencyNode): DependencyNode =
        filterGraph(group, module, graph)

    override suspend fun AmperModule.dependencyInsight(group: String, module: String, resolutionInput: ResolutionInput): DependencyNode {
        val graph = resolveDependencies(resolutionInput)
        return filterGraph(group, module, graph)
    }
}


