/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.frontend.dr.resolver.diagnostics

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
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolder
import org.jetbrains.amper.frontend.dr.resolver.fragmentDependencies
import org.jetbrains.amper.frontend.dr.resolver.mavenCoordinates
import org.jetbrains.amper.frontend.messages.PsiBuildProblem

@UsedInIdePlugin
fun DependencyNode.reportBuildProblems(problemReporter: ProblemReporter) {
    if (!messages.any { it.severity > Severity.INFO }) return

    val importantMessages = messages.filter { it.severity > Severity.INFO && it.toString().isNotBlank() }
    for (directDependency in fragmentDependencies) {
        // for every direct module dependency referencing this dependency node
        val psiElement = (directDependency.notation?.trace as? PsiTrace)?.psiElement

        if (psiElement != null) {
            for (message in importantMessages) {
                val level = when (message.severity) {
                    Severity.ERROR -> Level.Error
                    Severity.WARNING -> Level.Warning
                    else -> null
                }
                if (level != null) {
                    // todo (AB) : improve showing errors/warnings
                    // todo (AB) : - don't show error related to transitive dependency if it is among direct ones (show only there)
                    // todo (AB) : - improve error message about transitive dependencies
                    val buildProblem = DependencyBuildProblem(this, directDependency, message, level, psiElement)
                    problemReporter.reportMessage(buildProblem)
                }
            }
        }
    }
}

@UsedInIdePlugin
fun DependencyNode.reportOverriddenDirectModuleDependencies(reporter: ProblemReporter) {
    if (this is DirectFragmentDependencyNodeHolder
        && this.dependencyNode is MavenDependencyNode
        && this.dependencyNode.version != this.dependencyNode.dependency.version)
    {
        // for every direct module dependency referencing this dependency node
        val psiElement = (notation?.trace as? PsiTrace)?.psiElement
        if (psiElement != null) {
            reporter.reportMessage(
                ModuleDependencyWithOverriddenVersion(
                    this.dependencyNode.version,
                    this.dependencyNode.dependency.version,
                    this.dependencyNode.mavenCoordinates().toString(),
                    psiElement
                ))
        }
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

class ModuleDependencyWithOverriddenVersion(
    @UsedInIdePlugin
    val originalVersion: String,
    @UsedInIdePlugin
    val effectiveVersion: String,
    @UsedInIdePlugin
    val effectiveCoordinates: String,
    @UsedInIdePlugin
    override val element: PsiElement,
) : PsiBuildProblem(Level.Warning) {
    override val buildProblemId: BuildProblemId = ID
    override val message: String
        get() = "Declared dependency version is overridden, the actual version is $effectiveVersion"

    companion object {
        const val ID = "dependency.version.is.overridden"
    }
}