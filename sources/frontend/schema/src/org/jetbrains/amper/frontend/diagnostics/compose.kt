/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.isExplicitlySet
import org.jetbrains.amper.frontend.api.isSetInTemplate
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.DiagnosticId
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

object ComposeVersionWithDisabledCompose : AomSingleModuleDiagnosticFactory {
    @Deprecated(
        message = "Use ComposeVersionWithoutCompose.ID",
        replaceWith = ReplaceWith("ComposeVersionWithoutCompose.ID"),
    )
    val diagnosticId: BuildProblemId = ComposeVersionWithoutCompose.ID

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Trace?>()
        module.fragments.forEach { fragment ->
            val composeSettings = fragment.settings.compose
            if (!composeSettings.enabled) {
                val versionProp = composeSettings.versionDelegate
                val trace = versionProp.trace
                // We don't want to report anything if the version is set in a template, because users might just want
                // to set a central version for all modules (like a catalog) regardless of whether they use it.
                if (versionProp.isExplicitlySet && !versionProp.isSetInTemplate && reportedPlaces.add(trace)) {
                    problemReporter.reportMessage(ComposeVersionWithoutCompose(versionProp))
                }
            }
        }
    }
}

class ComposeVersionWithoutCompose(
    @UsedInIdePlugin
    val versionProp: SchemaValueDelegate<String>,
) : PsiBuildProblem(Level.Warning, BuildProblemType.InconsistentConfiguration) {
    companion object {
        const val ID = "compose.version.without.compose"
    }

    override val element: PsiElement
        get() = versionProp.extractPsiElement()

    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId: BuildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.ComposeVersionWithoutCompose

    override val message: String
        get() = SchemaBundle.message("compose.version.without.compose")
}
