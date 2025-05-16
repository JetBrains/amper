/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.annotations.Nls

object UselessSettingValue : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId
        get() = "setting.value.overrides.nothing"

    context(ProblemReporterContext) override fun AmperModule.analyze() {
        val visitor = object : SchemaValuesVisitor() {
            private val reportedPlaces = mutableSetOf<PsiElement>()

            override fun visitValue(it: ValueBase<*>) {
                val psiTrace = it.trace as? PsiTrace
                val precedingValue = psiTrace?.precedingValue
                val matchesPreceding = precedingValue?.value == it.value
                if (psiTrace != null && matchesPreceding && reportedPlaces.add(psiTrace.psiElement)) {
                    problemReporter.reportMessage(UselessSetting(it, precedingValue))
                }
                super.visitValue(it)
            }
        }
        fragments.forEach { fragment ->
            visitor.visit(fragment.settings)
        }
    }

}

private class UselessSetting(
    private val settingProp: ValueBase<*>,
    private val precedingValue: Traceable?,
) : PsiBuildProblem(Level.Redundancy) {
    override val element: PsiElement
        get() = settingProp.extractPsiElement()

    override val buildProblemId: BuildProblemId =
        UselessSettingValue.diagnosticId

    override val message: @Nls String
        get() = when {
            isInheritedFromCommon() -> SchemaBundle.message(
                messageKey = "setting.value.is.same.as.common",
            )

            else -> SchemaBundle.message(
                messageKey = "setting.value.is.same.as.base",
                formatLocation()
            )
        }

    private fun formatLocation(): String? {
        val precedingPsiElement = (precedingValue?.trace as? PsiTrace)?.psiElement ?: return "default"
        val precedingFile = precedingPsiElement.containingFile ?: return "default"
        val precedingLocation = precedingFile.takeIf { it.name != "module.yaml" && it.name != "module.amper" } ?: precedingFile.parent
        return precedingLocation?.name
    }

    private fun isInheritedFromCommon() =
        (settingProp.trace as? PsiTrace)?.psiElement?.containingFile == (precedingValue?.trace as? PsiTrace)?.psiElement?.containingFile
}
