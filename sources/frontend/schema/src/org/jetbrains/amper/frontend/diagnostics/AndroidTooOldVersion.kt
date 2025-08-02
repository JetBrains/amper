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
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

class AndroidTooOldVersion(
    override val element: PsiElement,
    used: AndroidVersion,
    minVersion: AndroidVersion,
) : PsiBuildProblem(Level.Error) {
    override val buildProblemId = AndroidTooOldVersionFactory.diagnosticId
    override val message = SchemaBundle.message(buildProblemId, used.versionNumber, minVersion.versionNumber)
}

object AndroidTooOldVersionFactory : MergedTreeDiagnostic {

    private val MINIMAL_ANDROID_VERSION = AndroidVersion.VERSION_21

    override val diagnosticId: BuildProblemId = "too.old.android.version"

    override fun analyze(root: MergedTree, minimalModule: MinimalModule, problemReporter: ProblemReporter)  =
        root.visitScalarProperties<AndroidSettings, AndroidVersion?>(
            AndroidSettings::compileSdk,
            AndroidSettings::minSdk,
            AndroidSettings::maxSdk,
            AndroidSettings::targetSdk,
        ) { prop, value ->
            if (value < MINIMAL_ANDROID_VERSION) {
                problemReporter.reportMessage(
                    AndroidTooOldVersion(
                        prop.value.trace.extractPsiElementOrNull() ?: return@visitScalarProperties,
                        value,
                        MINIMAL_ANDROID_VERSION,
                    )
                )
            }
        }
}
