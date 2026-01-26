/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.Key
import org.jetbrains.amper.dependency.resolution.MavenDependency
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.group
import org.jetbrains.amper.dependency.resolution.module
import org.jetbrains.amper.dependency.resolution.orUnspecified
import org.jetbrains.amper.dependency.resolution.originalVersion
import org.jetbrains.amper.dependency.resolution.resolvedVersion
import org.jetbrains.amper.dependency.resolution.version
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNode
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.DrDiagnosticsReporter
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.DrReporterContext
import org.jetbrains.amper.frontend.dr.resolver.moduleDependenciesResolver
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.annotations.Nls

class OverriddenDirectModuleDependencies : DrDiagnosticsReporter {
    override val level = Level.Warning

    override fun reportBuildProblemsForNode(
        node: DependencyNode,
        problemReporter: ProblemReporter,
        level: Level,
        context: DrReporterContext,
    ) {
        if (node !is DirectFragmentDependencyNode) return
        val dependencyNode = node.dependencyNode as? MavenDependencyNode ?: return

        val originalVersion = dependencyNode.originalVersion() ?: return

        if (originalVersion != dependencyNode.resolvedVersion()) {
            // for every direct module dependency referencing this dependency node
            val psiElement = node.notation.trace.extractPsiElementOrNull()
            if (psiElement != null) {
                val insightsCache = context.cache.computeIfAbsent(insightsCacheKey) { mutableMapOf() }
                val dependencyInsight = insightsCache.computeIfAbsent(dependencyNode.key) {
                    // todo (AB) : This call assume that conflict resolution is globally applied to the entire graph.
                    // todo (AB) : If graph contains more than one cluster of nodes resolved with help of different
                    // todo (AB) : conflict resolvers, the code won't work any longer.
                    // todo (AB) : Rule of thumb: this method should be called on the complete (!) subgraph that contains
                    // todo (AB) : all nodes resolved with the same conflict resolver.
                    moduleDependenciesResolver.dependencyInsight(
                        dependencyNode.group,
                        dependencyNode.module,
                        context.graphRoot,
                        resolvedVersionOnly = true,
                    )
                }
                problemReporter.reportMessage(
                    ModuleDependencyWithOverriddenVersion(
                        node,
                        overrideInsight = dependencyInsight,
                        psiElement
                    )
                )
            }
        }
    }

    companion object {
        private val insightsCacheKey =
            Key<MutableMap<Key<MavenDependency>, DependencyNode>>("OverriddenDirectModuleDependencies::insightsCache")
    }
}

class ModuleDependencyWithOverriddenVersion(
    @field:UsedInIdePlugin
    val originalNode: DirectFragmentDependencyNode,
    @field:UsedInIdePlugin
    val overrideInsight: DependencyNode,
    @field:UsedInIdePlugin
    override val element: PsiElement,
) : PsiBuildProblem(Level.Warning, BuildProblemType.Generic) {
    val dependencyNode: MavenDependencyNode
        get() = originalNode.dependencyNode as MavenDependencyNode
    val originalVersion: String
        get() = dependencyNode.originalVersion().orUnspecified()
    val effectiveVersion: String
        get() = dependencyNode.dependency.version.orUnspecified()
    val effectiveCoordinates: String
        get() = dependencyNode.key.name

    override val buildProblemId: BuildProblemId = ID
    override val message: @Nls String
        get() = when {
            dependencyNode.originalVersion != null -> FrontendDrBundle.message(
                messageKey = ID,
                dependencyNode.originalVersion, effectiveCoordinates, effectiveVersion
            )
            dependencyNode.versionFromBom != null -> FrontendDrBundle.message(
                messageKey = VERSION_FROM_BOM_IS_OVERRIDDEN_MESSAGE_ID,
                dependencyNode.versionFromBom, effectiveCoordinates, effectiveVersion
            )
            else -> error ("Version is not specified, should never happen at this stage")
        }

    companion object {
        const val ID = "dependency.version.is.overridden"
        const val VERSION_FROM_BOM_IS_OVERRIDDEN_MESSAGE_ID = "dependency.version.from.bom.is.overridden"
    }
}
