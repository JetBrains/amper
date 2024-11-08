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
import org.jetbrains.amper.frontend.AmperModule

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
    val fileCacheBuilder: FileCacheBuilder.() -> Unit = getDefaultAmperFileCacheBuilder(),
)

sealed interface DependenciesFlowType {
    data class ClassPathType(val scope: ResolutionScope, val platforms: Set<ResolutionPlatform>, val isTest: Boolean, val includedNonExportedNative: Boolean = true): DependenciesFlowType
    data class IdeSyncType(val aom: Model): DependenciesFlowType
}

interface ModuleDependenciesResolver {
    fun AmperModule.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit = getDefaultAmperFileCacheBuilder()
    ): ModuleDependencyNodeWithModule

    suspend fun DependencyNodeHolder.resolveDependencies(
        resolutionDepth: ResolutionDepth,
        resolutionLevel: ResolutionLevel = ResolutionLevel.NETWORK,
        downloadSources: Boolean = false
    )

    suspend fun AmperModule.resolveDependencies(resolutionInput: ResolutionInput): ModuleDependencyNodeWithModule

    suspend fun List<AmperModule>.resolveDependencies(resolutionInput: ResolutionInput): DependencyNodeHolder
}

open class DependencyNodeHolderWithNotation(
    name: String,
    children: List<DependencyNode>,
    templateContext: Context,
    @Suppress("UNUSED") // used in Idea Plugin
    val notation: DefaultScopedNotation? = null,
    parentNodes: List<DependencyNode> = emptyList(),
): DependencyNodeHolder(name, children, templateContext, parentNodes)

class ModuleDependencyNodeWithModule(
    val module: AmperModule,
    name: String,
    children: List<DependencyNode>,
    templateContext: Context,
    notation: DefaultScopedNotation? = null,
    parentNodes: List<DependencyNode> = emptyList(),
) : DependencyNodeHolderWithNotation(name, children, templateContext, notation, parentNodes = parentNodes)

class DirectFragmentDependencyNodeHolder(
    name: String,
    val dependencyNode: DependencyNode,
    val fragment: Fragment,
    templateContext: Context,
    notation: DefaultScopedNotation,
    parentNodes: List<DependencyNode> = emptyList(),
) : DependencyNodeHolderWithNotation(name, listOf(dependencyNode), templateContext, notation, parentNodes = parentNodes)
