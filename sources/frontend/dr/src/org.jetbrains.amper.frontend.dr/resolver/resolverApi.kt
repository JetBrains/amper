/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.PotatoModule

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
    val fileCacheBuilder: FileCacheBuilder.() -> Unit = {},
)

sealed interface DependenciesFlowType {
    data class ClassPathType(val scope: ResolutionScope, val platform: ResolutionPlatform, val isTest: Boolean): DependenciesFlowType
    data class IdeSyncType(val aom: Model): DependenciesFlowType
}

interface ModuleDependenciesResolver {
    fun PotatoModule.resolveDependenciesGraph(dependenciesFlowType: DependenciesFlowType, fileCacheBuilder: FileCacheBuilder.() -> Unit): ModuleDependencyNodeWithModule

    suspend fun DependencyNodeHolder.resolveDependencies(
        resolutionDepth: ResolutionDepth,
        resolutionLevel: ResolutionLevel = ResolutionLevel.NETWORK,
        downloadSources: Boolean = false
    )

    suspend fun PotatoModule.resolveDependencies(resolutionInput: ResolutionInput): ModuleDependencyNodeWithModule

    suspend fun List<PotatoModule>.resolveDependencies(resolutionInput: ResolutionInput): DependencyNodeHolder
}

open class DependencyNodeHolderWithNotation(
    name: String,
    children: List<DependencyNode>,
    @Suppress("UNUSED") // used in Idea Plugin
    val notation: DefaultScopedNotation? = null,
    templateContext: Context = Context(),
    parentNodes: List<DependencyNode> = emptyList(),
): DependencyNodeHolder(name, children, templateContext, parentNodes)

class ModuleDependencyNodeWithModule(
    val module: PotatoModule,
    name: String,
    children: List<DependencyNode>,
    notation: DefaultScopedNotation? = null,
    templateContext: Context = Context(),
    parentNodes: List<DependencyNode> = emptyList(),
) : DependencyNodeHolderWithNotation(name, children, notation, templateContext, parentNodes = parentNodes)

class DirectFragmentDependencyNodeHolder(
    name: String,
    val dependencyNode: DependencyNode,
    val fragment: Fragment,
    notation: DefaultScopedNotation,
    templateContext: Context = Context(),
    parentNodes: List<DependencyNode> = emptyList(),
) : DependencyNodeHolderWithNotation(name, listOf(dependencyNode), notation, templateContext, parentNodes = parentNodes)
