/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.project.AmperProjectContext
import kotlin.reflect.KProperty0

object ComposeVersionWithDisabledCompose : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "compose.version.without.compose"

    context(ProblemReporterContext) override fun PotatoModule.analyze(projectContext: AmperProjectContext) {
        val reportedPlaces = mutableSetOf<Trace?>()
        fragments.forEach { fragment ->
            val settings = fragment.settings.compose
            if (settings.version != null && !settings.enabled) {
                val versionProp = settings::version
                if (!reportedPlaces.add(versionProp.valueBase?.trace)) return@forEach
                problemReporter.reportMessage(
                    ComposeVersionWithoutCompose(
                        versionProp
                    )
                )
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