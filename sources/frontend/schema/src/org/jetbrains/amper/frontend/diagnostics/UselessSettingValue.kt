/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.SchemaValuesVisitor
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.project.AmperProjectContext
import kotlin.reflect.jvm.javaField

object UselessSettingValue : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId
        get() = "setting.value.overrides.nothing"

    context(ProblemReporterContext) override fun PotatoModule.analyze(projectContext: AmperProjectContext) {
        val reportedPlaces = mutableSetOf<PsiElement>()
        val visitor = object : SchemaValuesVisitor() {
            override fun visitValue(it: ValueBase<*>) {
                val psiTrace = it.trace as? PsiTrace
                val precedingValue = psiTrace?.precedingValue
                val isDefault = precedingValue == null && it.value == it.default?.value
                if (psiTrace != null && reportedPlaces.add(psiTrace.psiElement) && accepts(it, isDefault)) {
                    if (isDefault || precedingValue?.value == it.value) {
                        problemReporter.reportMessage(
                            UselessSetting(it, precedingValue)
                        )
                    }
                }
                super.visitValue(it)
            }

            private fun accepts(base: ValueBase<*>, isDefault: Boolean): Boolean {
                // a hack for the serialization settings section,
                // its presence changes semantics, so we don't report "useless" defaults for it,
                // see https://youtrack.jetbrains.com/issue/AMPER-910 for details
                return !isDefault
                        || base.property.javaField?.declaringClass?.canonicalName != "org.jetbrains.amper.frontend.schema.SerializationSettings"
            }
        }
        fragments.forEach { fragment ->
            visitor.visit(fragment.settings)
        }
    }

}

private class UselessSetting(
    private val settingProp: ValueBase<*>,
    private val precedingValue: ValueBase<*>?,
) : PsiBuildProblem(Level.Redundancy) {
    override val element: PsiElement
        get() = settingProp.extractPsiElement()

    override val buildProblemId: BuildProblemId =
        UselessSettingValue.diagnosticId

    override val message: String
        get() = when  {
            precedingValue?.trace == null -> SchemaBundle.message(
                messageKey = "setting.value.is.same.as.default",
            )
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
        if (precedingPsiElement.containingFile?.name != "module.yaml"
            && precedingPsiElement.containingFile?.name != "module.amper") {
            return precedingPsiElement.containingFile?.name
        }
        return precedingPsiElement.containingFile.parent?.name
    }

    private fun isInheritedFromCommon() =
        (settingProp.trace as? PsiTrace)?.psiElement?.containingFile == (precedingValue?.trace as? PsiTrace)?.psiElement?.containingFile
}