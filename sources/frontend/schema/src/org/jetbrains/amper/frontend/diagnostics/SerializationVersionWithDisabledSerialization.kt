/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.isDefault
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.annotations.Nls
import kotlin.reflect.KProperty0

object SerializationVersionWithDisabledSerialization : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "serialization.version.without.serialization"

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Trace?>()
        module.fragments.forEach { fragment ->
            val settings = fragment.settings.kotlin.serialization
            val versionProp = settings::version
            if (!versionProp.isDefault && !settings.enabled) {
                if (!reportedPlaces.add(versionProp.valueBase.trace)) return@forEach
                problemReporter.reportMessage(SerializationVersionWithoutSerialization(versionProp))
            }
        }
    }
}

class SerializationVersionWithoutSerialization(
    @UsedInIdePlugin
    val versionProp: KProperty0<String?>,
) : PsiBuildProblem(Level.Warning, BuildProblemType.InconsistentConfiguration) {
    override val element: PsiElement
        get() = versionProp.extractPsiElement()

    override val buildProblemId: BuildProblemId = SerializationVersionWithDisabledSerialization.diagnosticId

    override val message: @Nls String
        get() = SchemaBundle.message(messageKey = buildProblemId)
}
