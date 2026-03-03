/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.contexts.MinimalModule
import org.jetbrains.amper.frontend.diagnostics.helpers.visitEnumProperties
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElementOrNull
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidVersion
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.DiagnosticId
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

class AndroidTooOldVersion(
    override val element: PsiElement,
    used: AndroidVersion,
    minVersion: AndroidVersion,
) : PsiBuildProblem(Level.Error, BuildProblemType.Generic) {
    companion object {
        const val ID = "too.old.android.version"
    }

    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.AndroidVersionTooOld
    override val message = SchemaBundle.message("too.old.android.version", used.versionNumber, minVersion.versionNumber)
}

object AndroidTooOldVersionFactory : TreeDiagnosticFactory {

    private val MINIMAL_ANDROID_VERSION = AndroidVersion.VERSION_21
    @Deprecated(
        message = "Use AndroidTooOldVersion.ID",
        replaceWith = ReplaceWith("AndroidTooOldVersion.ID"),
    )
    val diagnosticId: BuildProblemId = AndroidTooOldVersion.ID

    override fun analyze(root: TreeNode, minimalModule: MinimalModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<PsiElement>() // somehow the computed properties lead to duplicate reports
        root.visitEnumProperties<AndroidSettings, AndroidVersion?>(
            AndroidSettings::compileSdk,
            AndroidSettings::minSdk,
            AndroidSettings::maxSdk,
            AndroidSettings::targetSdk,
        ) { prop, value ->
            val versionTraceElement = prop.value.trace.extractPsiElementOrNull() ?: return@visitEnumProperties
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
