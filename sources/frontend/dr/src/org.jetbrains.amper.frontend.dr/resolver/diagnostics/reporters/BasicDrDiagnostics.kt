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
import org.jetbrains.amper.dependency.resolution.Message
import org.jetbrains.amper.dependency.resolution.Severity
import org.jetbrains.amper.dependency.resolution.message
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableVersion
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolder
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.DrDiagnosticsReporter
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.mapLevelToSeverity
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.mapSeverityToLevel
import org.jetbrains.amper.frontend.dr.resolver.fragmentDependencies
import org.jetbrains.amper.frontend.getLineAndColumnRangeInPsiFile
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull

class BasicDrDiagnostics: DrDiagnosticsReporter{

    override val level = Level.Error

    override fun reportBuildProblemsForNode(
        node: DependencyNode,
        problemReporter: ProblemReporter,
        level: Level
    ) {
        val severity = level.mapLevelToSeverity() ?: return
        val importantMessages = node.messages.filter { it.severity >= severity && it.toString().isNotBlank() }

        if (importantMessages.isEmpty()) return

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
                    val buildProblem = DependencyBuildProblem(node, directDependency, refinedErrorMessage, msgLevel, psiElement)
                    problemReporter.reportMessage(buildProblem)
                }
            }
        }
    }

    private fun refineErrorMessage(message: Message, node: DependencyNode, directDependency: DirectFragmentDependencyNodeHolder): Message {
        // direct node could have version traces only
        if (message.severity >= Severity.ERROR && node == directDependency.dependencyNode && node is MavenDependencyNode) {
            val versionTrace = (directDependency.notation as? org.jetbrains.amper.frontend.MavenDependency)?.coordinates?.resolveVersionTrace()
            if (versionTrace != null && versionTrace.value == node.dependency.version && versionTrace.trace is PsiTrace) {
                val psiElement = (versionTrace.trace as? PsiTrace)?.psiElement
                if (psiElement != null && versionTrace.value == node.dependency.version) {
                    val range = getLineAndColumnRangeInPsiFile(psiElement)
                    return message.copy(
                        text = message.text +
                                ". The version ${node.dependency.version} is defined at ${psiElement.containingFile.name}:${range.start.line}"
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
            errorMessage.message
        }  else {
            "Transitive dependency problem: ${this.errorMessage.message}"
        }

    companion object {
        const val ID = "dependency.problem"
    }
}