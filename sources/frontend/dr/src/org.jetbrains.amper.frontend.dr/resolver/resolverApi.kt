/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionLevel
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.SpanBuilderSource
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.Fragment
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
    val fileCacheBuilder: FileCacheBuilder.() -> Unit,
    val spanBuilder: SpanBuilderSource?= null,
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
        spanBuilder: SpanBuilderSource? = null,
    ): ModuleDependencyNodeWithModule

    fun List<AmperModule>.resolveDependenciesGraph(
        dependenciesFlowType: DependenciesFlowType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        spanBuilder: SpanBuilderSource? = null,
    ): DependencyNodeHolder

    suspend fun DependencyNodeHolder.resolveDependencies(
        resolutionDepth: ResolutionDepth,
        resolutionLevel: ResolutionLevel = ResolutionLevel.NETWORK,
        downloadSources: Boolean = false
    )

    suspend fun AmperModule.resolveDependencies(resolutionInput: ResolutionInput): ModuleDependencyNodeWithModule

    /**
     * Returned filtered dependencies graph,
     * containing paths from the root to the maven dependency node corresponding to the given coordinates (group and module)
     * and having the version equal to the actual resolved version of this dependency in the graph.
     * If the resolved dependency version is enforced by constraint, then the path to that constraint is presented
     * in a returned graph together with paths to all versions of this dependency.
     *
     * Every node of the returned graph is of the type [DependencyNodeWithChildren] holding the corresponding node from the original graph inside.
     */
    suspend fun AmperModule.dependencyInsight(
        group: String,
        module: String,
        resolutionInput: ResolutionInput
    ): DependencyNode

    fun dependencyInsight(group: String, module: String, node: DependencyNode, resolvedVersionOnly: Boolean = false): DependencyNode

    suspend fun List<AmperModule>.resolveDependencies(resolutionInput: ResolutionInput): DependencyNodeHolder
}

open class DependencyNodeHolderWithNotation(
    name: String,
    children: List<DependencyNode>,
    templateContext: Context,
    open val notation: Notation? = null,
    parentNodes: List<DependencyNode> = emptyList(),
) : DependencyNodeHolder(name, children, templateContext, parentNodes)

class ModuleDependencyNodeWithModule(
    val module: AmperModule,
    name: String,
    children: List<DependencyNode>,
    templateContext: Context,
    override val notation: DefaultScopedNotation? = null,
    parentNodes: List<DependencyNode> = emptyList(),
) : DependencyNodeHolderWithNotation(name, children, templateContext, notation, parentNodes = parentNodes)

class DirectFragmentDependencyNodeHolder(
    val dependencyNode: DependencyNode,
    val fragment: Fragment,
    templateContext: Context,
    override val notation: Notation,
    parentNodes: List<DependencyNode> = emptyList(),
    override val messages: List<Message> = emptyList(),
) : DependencyNodeHolderWithNotation(
    name = "${fragment.module.userReadableName}:${fragment.name}:${dependencyNode}${traceInfo(notation)}",
    listOf(dependencyNode), templateContext, notation, parentNodes = parentNodes
)

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
