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
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.schema.AndroidVersion
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.problems.reporting.BuildProblemId
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.DiagnosticId
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.annotations.Nls

class AndroidVersionShouldBeAtLeastMinSdk(
    @UsedInIdePlugin
    val versionProp: SchemaValueDelegate<out AndroidVersion?>,
    @UsedInIdePlugin
    val minSdkVersion: AndroidVersion
) : PsiBuildProblem(Level.Error, BuildProblemType.InconsistentConfiguration) {
    companion object {
        const val ID = "android.version.should.be.at.least.min.sdk"
    }

    override val element: PsiElement
        get() = versionProp.extractPsiElement()

    @Deprecated("Should be replaced with `diagnosticId` property", replaceWith = ReplaceWith("diagnosticId"))
    override val buildProblemId: BuildProblemId = ID
    override val diagnosticId: DiagnosticId = FrontendDiagnosticId.AndroidVersionShouldBeAtLeastMinSdk

    override val message: @Nls String
        get() = SchemaBundle.message(
            messageKey = "android.version.should.be.at.least.min.sdk",
            versionProp.name,
            versionProp.value?.versionNumber,
            minSdkVersion.versionNumber
        )
}

object AndroidVersionShouldBeAtLeastMinSdkFactory : AomSingleModuleDiagnosticFactory {
    @Deprecated(
        message = "Use AndroidVersionShouldBeAtLeastMinSdk.ID",
        replaceWith = ReplaceWith("AndroidVersionShouldBeAtLeastMinSdk.ID"),
    )
    val diagnosticId: BuildProblemId = AndroidVersionShouldBeAtLeastMinSdk.ID

    override fun analyze(module: AmperModule, problemReporter: ProblemReporter) {
        val reportedPlaces = mutableSetOf<Trace?>()
        module.fragments.forEach { fragment ->
            val settings = fragment.settings.android
            val usedVersions = listOf(
                settings.compileSdkDelegate,
                settings.maxSdkDelegate,
                settings.targetSdkDelegate,
            ).filter { it.value != null }
            val minSdkVersion = settings.minSdk
            for (versionProp in usedVersions) {
                val version = versionProp.value ?: continue
                if (version >= minSdkVersion) continue
                if (!reportedPlaces.add(versionProp.trace)) continue

                problemReporter.reportMessage(
                    AndroidVersionShouldBeAtLeastMinSdk(
                        versionProp,
                        minSdkVersion = minSdkVersion,
                    )
                )
            }
        }
    }
}
