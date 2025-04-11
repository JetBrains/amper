/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters

import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.BuildProblemImpl
import org.jetbrains.amper.core.messages.GlobalBuildProblemSource
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NonIdealDiagnostic
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.diagnostics.Message
import org.jetbrains.amper.dependency.resolution.diagnostics.SimpleMessage
import org.jetbrains.amper.dependency.resolution.diagnostics.Severity
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
            val psiElement = directDependency.notation?.trace?.extractPsiElementOrNull()

            if (psiElement != null) {
                for (message in importantMessages) {
                    val msgLevel = message.mapSeverityToLevel()
                    // todo (AB) : improve showing errors/warnings
                    // todo (AB) : - don't show error related to transitive dependency if it is among direct ones (show only there)
                    // todo (AB) : - improve error message about transitive dependencies

                    val refinedErrorMessage = refineErrorMessage(message, node, directDependency)
                    val buildProblem =
                        DependencyBuildProblem(node, directDependency, refinedErrorMessage, msgLevel, psiElement)
                    problemReporter.reportMessage(buildProblem)
                }
            }
        }
    }

    private fun refineErrorMessage(
        message: Message,
        node: DependencyNode,
        directDependency: DirectFragmentDependencyNodeHolder
    ): Message {
        if (message.severity < Severity.ERROR || message !is SimpleMessage) return message

        // direct node could have version traces only
        if (node == directDependency.dependencyNode && node is MavenDependencyNode) {
            val versionTrace = (directDependency.notation as? MavenDependencyBase)
                ?.coordinates
                ?.resolveVersionTrace()
            if (versionTrace != null && versionTrace.value == node.dependency.version && versionTrace.trace is PsiTrace) {
                // Version trace points to the place the version is defined
                val psiElement = (versionTrace.trace as? PsiTrace)?.psiElement
                if (psiElement != null) {
                    val range = getLineAndColumnRangeInPsiFile(psiElement)

                    val dependencyPsiFilePath =
                        directDependency.notation.coordinates.trace?.extractPsiElementOrNull()?.containingFile?.virtualFile?.toNioPathOrNull()
                    val versionFilePath = psiElement.containingFile.virtualFile.toNioPathOrNull()

                    val relativeVersionFilePath =
                        if (dependencyPsiFilePath != null && versionFilePath != null && dependencyPsiFilePath != versionFilePath)
                            dependencyPsiFilePath.relativize(versionFilePath)
                        else psiElement.containingFile.name

                    return message.copy(
                        text = message.text +
                                ". The version ${node.dependency.version} is defined at $relativeVersionFilePath:${range.start.line}"
                    )
                }
            }
        }

        return message
    }

    private fun Traceable.resolveVersionTrace(): TraceableVersion? {
        return (this as? TraceableVersion) ?: this.trace?.computedValueTrace?.resolveVersionTrace()
    }
}

class DependencyBuildProblem(
    @UsedInIdePlugin
    val problematicDependency: DependencyNode,
    val directFragmentDependency: DirectFragmentDependencyNodeHolder,
    val errorMessage: Message,
    level: Level,
    @UsedInIdePlugin
    override val element: PsiElement,
) : PsiBuildProblem(level) {
    override val buildProblemId: BuildProblemId = ID
    override val message: String
        get() = if (problematicDependency.parents.contains(directFragmentDependency)) {
            errorMessage.detailedMessage
        } else {
            FrontendDrBundle.message(
                messageKey = DEPENDENCY_PROBLEM_TRANSITIVE_KEY,
                this.errorMessage.detailedMessage
            )
        }

    companion object {
        const val ID = "dependency.problem"
        const val DEPENDENCY_PROBLEM_TRANSITIVE_KEY = "dependency.problem.transitive"
    }
}
