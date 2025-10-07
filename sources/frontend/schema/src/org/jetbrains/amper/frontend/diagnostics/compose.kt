/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.isExplicitlySet
import org.jetbrains.amper.frontend.api.isSetInTemplate
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import kotlin.reflect.KProperty0

object ComposeVersionWithDisabledCompose : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "compose.version.without.compose"

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Trace?>()
        module.fragments.forEach { fragment ->
            val composeSettings = fragment.settings.compose
            if (!composeSettings.enabled) {
                val versionProp = composeSettings::version
                val trace = versionProp.schemaDelegate.trace
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
    val versionProp: KProperty0<String?>,
) : PsiBuildProblem(Level.Warning, BuildProblemType.InconsistentConfiguration) {
    override val element: PsiElement
        get() = versionProp.extractPsiElement()

    override val buildProblemId: BuildProblemId =
        ComposeVersionWithDisabledCompose.diagnosticId

    override val message: String
        get() = SchemaBundle.message(
            messageKey = buildProblemId
        )
}