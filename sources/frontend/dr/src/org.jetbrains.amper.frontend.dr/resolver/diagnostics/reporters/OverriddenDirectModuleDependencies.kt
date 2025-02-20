/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.originalVersion
import org.jetbrains.amper.dependency.resolution.resolvedVersion
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolder
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.DrDiagnosticsReporter
import org.jetbrains.amper.frontend.dr.resolver.moduleDependenciesResolver
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull

class OverriddenDirectModuleDependencies: DrDiagnosticsReporter{

    override val level = Level.Warning

    override fun reportBuildProblemsForNode(
        node: DependencyNode,
        problemReporter: ProblemReporter,
        level: Level,
        graphRoot: DependencyNode,
    ) {
        if (node is DirectFragmentDependencyNodeHolder
            && node.dependencyNode is MavenDependencyNode
            && node.dependencyNode.version != node.dependencyNode.dependency.version)
        {
            // for every direct module dependency referencing this dependency node
            val psiElement = node.notation?.trace?.extractPsiElementOrNull()
            if (psiElement != null) {
                problemReporter.reportMessage(
                    ModuleDependencyWithOverriddenVersion(
                        node.dependencyNode.version,
                        node.dependencyNode.dependency.version,
                        null,
//                        node.dependencyNode.overriddenByChain(graphRoot),  // this wordy details could be uncommented and passed instead of null and then shown in IDEA by request
                        node.dependencyNode.key.name,
                        psiElement
                    ))
            }
        }
    }

    private fun MavenDependencyNode.overriddenByChain(graph: DependencyNode): String? {
        return if (originalVersion() == resolvedVersion()) {
            null
        } else {
            val subgraph = moduleDependenciesResolver.dependencyInsight(this.group, this.module, graph)
            return subgraph.prettyPrint().trimEnd()
        }
    }
}

class ModuleDependencyWithOverriddenVersion(
    @UsedInIdePlugin
    val originalVersion: String,
    @UsedInIdePlugin
    val effectiveVersion: String,
    val overriddenBy: String?,
    @UsedInIdePlugin
    val effectiveCoordinates: String,
    @UsedInIdePlugin
    override val element: PsiElement,
) : PsiBuildProblem(Level.Warning) {
    override val buildProblemId: BuildProblemId = ID
    override val message: String
        get() = FrontendDrBundle.message(
            messageKey = ID,
            originalVersion, effectiveCoordinates, effectiveVersion
        )

    val additionalMessage: String?
        get() = overriddenBy?.let{ FrontendDrBundle.message(messageKey = ADDITIONAL_MESSAGE_KEY, it) }

    companion object {
        const val ID = "dependency.version.is.overridden"
        const val ADDITIONAL_MESSAGE_KEY = "dependency.version.is.overridden.additional"
    }
}