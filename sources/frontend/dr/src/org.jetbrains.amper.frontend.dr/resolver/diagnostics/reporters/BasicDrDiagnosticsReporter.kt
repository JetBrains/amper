/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.detailedMessage
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableVersion
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolder
import org.jetbrains.amper.frontend.dr.resolver.FrontendDrBundle
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.DrDiagnosticsReporter
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.mapLevelToSeverity
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.mapSeverityToLevel
import org.jetbrains.amper.frontend.dr.resolver.fragmentDependencies
import org.jetbrains.amper.frontend.getLineAndColumnRangeInPsiFile
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemImpl
import org.jetbrains.amper.problems.reporting.GlobalBuildProblemSource
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.annotations.Nls
import java.io.File

object BasicDrDiagnosticsReporter : DrDiagnosticsReporter {
    override val level = Level.Error

    override fun reportBuildProblemsForNode(
        node: DependencyNode,
        problemReporter: ProblemReporter,
        level: Level,
        graphRoot: DependencyNode,
    ) {
        val severity = level.mapLevelToSeverity() ?: return
        val importantMessages = node.messages.filter { it.severity >= severity && it.toString().isNotBlank() }

        if (importantMessages.isEmpty()) return

        if (node.fragmentDependencies.isEmpty()) {
            reportMessagesAsGlobal(importantMessages, problemReporter)
        } else {
            reportMessagesOnParentPsiNodes(node, importantMessages, problemReporter)
        }
    }

    @OptIn(NonIdealDiagnostic::class)
    private fun reportMessagesAsGlobal(importantMessages: List<Message>, problemReporter: ProblemReporter) {
        for (message in importantMessages) {
            val msgLevel = message.mapSeverityToLevel()
            val buildProblem = BuildProblemImpl(
                "dependency.problem.no.psi",
                GlobalBuildProblemSource,
                message.detailedMessage,
                msgLevel
            )
            problemReporter.reportMessage(buildProblem)
        }
    }

    private fun reportMessagesOnParentPsiNodes(
        node: DependencyNode,
        importantMessages: List<Message>,
        problemReporter: ProblemReporter
    ) {
        for (directDependency in node.fragmentDependencies) {
            // for every direct module dependency referencing this dependency node
            val psiElement = directDependency.notation.trace.extractPsiElementOrNull()

            if (psiElement != null) {
                for (message in importantMessages) {
                    val msgLevel = message.mapSeverityToLevel()
                    if (node.isTransitiveFor(directDependency)) {
                        if (!message.reportTransitive) continue
                        // If the message is already reported on the direct dependency node,
                        // skip reporting it on the transitive node.
                        if (directDependency.dependencyNode.messages.any { it.id == message.id }) continue
                    }

                    val buildProblem = DependencyBuildProblem(
                        problematicDependency = node,
                        directFragmentDependency = directDependency,
                        errorMessage = message,
                        level = msgLevel,
                        element = psiElement,
                        versionDefinition = findVersionDefinition(node, directDependency, psiElement),
                    )
                    problemReporter.reportMessage(buildProblem)
                }
            }
        }
    }

    /**
     * Returns the definition of the version of the [node] if it is defined not in the same place
     * where the [directDependency] itself (e.g., the version comes from the settings).
     *
     * @param node Dependency node that represents the problematic dependency in the graph
     * @param directDependency Dependency node that references the [node] from the module file
     * @param dependencyElement PSI element trace of [directDependency]
     */
    private fun findVersionDefinition(
        node: DependencyNode,
        directDependency: DirectFragmentDependencyNodeHolder,
        dependencyElement: PsiElement,
    ): VersionDefinition? {
        if (node !is MavenDependencyNode) return null
        val notation = directDependency.notation as? MavenDependencyBase ?: return null

        val resolvedVersion = notation.coordinates.resolveVersion() ?: return null

        val versionTrace = resolvedVersion.trace

        if (versionTrace !is PsiTrace) return null
        if (versionTrace == directDependency.notation.trace) return null

        if (resolvedVersion.value != node.dependency.version) return null

        val dependencyVirtualFile = dependencyElement.containingFile?.virtualFile ?: return null
        val versionFile = versionTrace.psiElement.containingFile?.virtualFile ?: return null

        val relativePath = VfsUtilCore.findRelativePath(dependencyVirtualFile, versionFile, File.separatorChar)?.takeIf {
            dependencyVirtualFile != versionFile
        } ?: versionTrace.psiElement.containingFile.name
        return VersionDefinition(versionTrace.psiElement, relativePath)
    }

    private fun Traceable.resolveVersion(): TraceableVersion? {
        return (this as? TraceableVersion) ?: this.trace?.computedValueTrace?.resolveVersion()
    }
}

class DependencyBuildProblem(
    @field:UsedInIdePlugin
    val problematicDependency: DependencyNode,
    val directFragmentDependency: DirectFragmentDependencyNodeHolder,
    val errorMessage: Message,
    level: Level,
    @field:UsedInIdePlugin
    override val element: PsiElement,
    @field:UsedInIdePlugin
    val versionDefinition: VersionDefinition?,
) : PsiBuildProblem(level) {
    override val buildProblemId: BuildProblemId = ID

    override val message: @Nls String
        get() = buildString {
            if (!isTransitive) {
                append(errorMessage.detailedMessage)
            } else {
                append(
                    FrontendDrBundle.message(
                        messageKey = "dependency.problem.transitive",
                        directFragmentDependency.dependencyNode,
                        problematicDependency,
                        errorMessage.detailedMessage,
                    )
                )
            }
            val versionDefinitionMessage = getVersionDefinitionMessage()
            if (versionDefinitionMessage != null) {
                appendLine()
                appendLine()
                append(versionDefinitionMessage)
            }
        }

    val isTransitive: Boolean
        get() = problematicDependency.isTransitiveFor(directFragmentDependency)

    private fun getVersionDefinitionMessage(): @Nls String? {
        versionDefinition ?: return null
        val node = problematicDependency as? MavenDependencyNode ?: return null
        val range = getLineAndColumnRangeInPsiFile(versionDefinition.versionElement)

        return FrontendDrBundle.message(
            "dependency.problem.version.definition",
            node.dependency.version,
            "${versionDefinition.relativePath}:${range.start.line}:${range.start.column}",
        )
    }

    companion object {
        const val ID = "dependency.problem"
    }
}

/**
 * Information about where the version of the dependency is defined.
 *
 * @param versionElement PSI element containing the version definition
 * @param relativePath Relative path from the file containing dependency to the file containing the version definition
 * @see BasicDrDiagnosticsReporter.findVersionDefinition
 */
data class VersionDefinition(val versionElement: PsiElement, val relativePath: String)

internal fun DependencyNode.isTransitiveFor(fragmentDependency: DirectFragmentDependencyNodeHolder): Boolean =
    this != fragmentDependency && fragmentDependency !in parents
