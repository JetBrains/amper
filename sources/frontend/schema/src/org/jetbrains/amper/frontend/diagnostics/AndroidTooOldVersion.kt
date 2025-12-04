/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.visitScalarProperties
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidVersion
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

class AndroidTooOldVersion(
    override val element: PsiElement,
    used: AndroidVersion,
    minVersion: AndroidVersion,
) : PsiBuildProblem(Level.Error, BuildProblemType.Generic) {
    override val buildProblemId = AndroidTooOldVersionFactory.diagnosticId
    override val message = SchemaBundle.message(buildProblemId, used.versionNumber, minVersion.versionNumber)
}

object AndroidTooOldVersionFactory : TreeDiagnostic {

    private val MINIMAL_ANDROID_VERSION = AndroidVersion.VERSION_21

    override val diagnosticId: BuildProblemId = "too.old.android.version"

    override fun analyze(root: TreeValue<*>, minimalModule: MinimalModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<PsiElement>() // somehow the computed properties lead to duplicate reports
        root.visitScalarProperties<AndroidSettings, AndroidVersion?>(
            AndroidSettings::compileSdk,
            AndroidSettings::minSdk,
            AndroidSettings::maxSdk,
            AndroidSettings::targetSdk,
        ) { prop, value ->
            val versionTraceElement = prop.value.trace.extractPsiElementOrNull() ?: return@visitScalarProperties
            if (value < MINIMAL_ANDROID_VERSION && reportedPlaces.add(versionTraceElement)) {
                problemReporter.reportMessage(
                    AndroidTooOldVersion(
                        element = versionTraceElement,
                        used = value,
                        minVersion = MINIMAL_ANDROID_VERSION,
                    )
                )
            }
        }
    }
}
