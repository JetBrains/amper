/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.visitStringProperties
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.schema.KotlinCompilerVersionPattern
import org.jetbrains.amper.frontend.schema.KotlinSettings
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.DiagnosticId
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

object KotlinCompilerVersionDiagnosticsFactory : TreeDiagnosticFactory {

    private val MinimumSupportedKotlinVersion = ComparableVersion("2.1.10")

    @Deprecated(
        message = "Use InvalidKotlinCompilerVersion.ID or KotlinCompilerVersionTooLow.ID",
    )
    val diagnosticId: BuildProblemId = "kotlin.compiler.version.diagnostics"

    override fun analyze(root: TreeNode, minimalModule: MinimalModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Trace>() // somehow the computed properties lead to duplicate reports
        root.visitStringProperties<KotlinSettings>(KotlinSettings::version) { prop, value ->
            val versionTrace = prop.value.trace
            if (!KotlinCompilerVersionPattern.matches(value) && reportedPlaces.add(versionTrace)) {
                problemReporter.reportMessage(
                    InvalidKotlinCompilerVersion(
                        element = versionTrace.extractPsiElementOrNull() ?: return@visitStringProperties,
                        actualVersion = value,
                    )
                )
            } else if (ComparableVersion(value) < MinimumSupportedKotlinVersion && reportedPlaces.add(versionTrace)) {
                problemReporter.reportMessage(
                    KotlinCompilerVersionTooLow(
                        element = versionTrace.extractPsiElementOrNull() ?: return@visitStringProperties,
                        actualVersion = value,
                        minVersion = MinimumSupportedKotlinVersion.toString(),
                    )
                )
            }
        }
    }
}

class InvalidKotlinCompilerVersion(
    override val element: PsiElement,
    val actualVersion: String,
) : PsiBuildProblem(Level.Error, BuildProblemType.Generic) {
    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.InvalidKotlinCompilerVersion
    override val message = SchemaBundle.message("invalid.kotlin.compiler.version", actualVersion)

    companion object {
        const val ID = "invalid.kotlin.compiler.version"
    }
}

class KotlinCompilerVersionTooLow(
    override val element: PsiElement,
    val actualVersion: String,
    val minVersion: String,
) : PsiBuildProblem(Level.Error, BuildProblemType.Generic) {
    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.KotlinCompilerVersionTooLow
    override val message = SchemaBundle.message("kotlin.compiler.version.too.low", actualVersion, minVersion)

    companion object {
        const val ID = "kotlin.compiler.version.too.low"
    }
}
