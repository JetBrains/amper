/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.aomBuilder.chooseComposeVersion
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.reportBundleError
import kotlin.reflect.KProperty0

object InconsistentComposeVersion : AomModelDiagnosticFactory {
    const val diagnosticId: BuildProblemId = "inconsistent.compose.versions"

    override fun analyze(model: Model, problemReporter: ProblemReporter) {
        val chosenComposeVersionForModel = chooseComposeVersion(model) ?: return

        val mismatchedComposeSettings = model.modules
            .map { it.rootFragment.settings.compose }
            .filter { it.version != chosenComposeVersionForModel }

        mismatchedComposeSettings.forEach {
            val sourceProperty = if (!it::version.isDefault) it::version else it::enabled
            problemReporter.reportBundleError(
                source = sourceProperty.asBuildProblemSource(),
                messageKey = diagnosticId,
                chosenComposeVersionForModel,
                level = Level.Fatal,
            )
        }
    }
}

object ComposeVersionWithDisabledCompose : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "compose.version.without.compose"

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Trace?>()
        module.fragments.forEach { fragment ->
            val settings = fragment.settings.compose
            if (!settings.enabled) {
                val versionProp = settings::version
                val trace = versionProp.valueBase?.trace
                if (!versionProp.isDefault && reportedPlaces.add(trace))
                    problemReporter.reportMessage(ComposeVersionWithoutCompose(versionProp))
            }
        }
    }
}

class ComposeVersionWithoutCompose(
    @UsedInIdePlugin
    val versionProp: KProperty0<String?>,
) : PsiBuildProblem(Level.Warning) {
    override val element: PsiElement
        get() = versionProp.extractPsiElement()

    override val buildProblemId: BuildProblemId =
        ComposeVersionWithDisabledCompose.diagnosticId

    override val message: String
        get() = SchemaBundle.message(
            messageKey = buildProblemId
        )
}