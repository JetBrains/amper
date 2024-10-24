/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.schema.AndroidVersion
import kotlin.reflect.KProperty0

class AndroidVersionShouldBeAtLeastMinSdk(
    @UsedInIdePlugin
    val versionProp: KProperty0<AndroidVersion?>,
    @UsedInIdePlugin
    val minSdkVersion: AndroidVersion
) : PsiBuildProblem(Level.Error) {
    override val element: PsiElement
        get() = versionProp.extractPsiElement()

    override val buildProblemId: BuildProblemId =
        AndroidVersionShouldBeAtLeastMinSdkFactory.diagnosticId

    override val message: String
        get() = SchemaBundle.message(
            messageKey = buildProblemId,
            versionProp.name,
            versionProp.get()?.versionNumber,
            minSdkVersion.versionNumber
        )
}

object AndroidVersionShouldBeAtLeastMinSdkFactory : AomSingleModuleDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "android.version.should.be.at.least.min.sdk"

    context(ProblemReporterContext) override fun AmperModule.analyze() {
        val reportedPlaces = mutableSetOf<Trace?>()
        fragments.forEach { fragment ->
            val settings = fragment.settings.android
            val usedVersions = listOf(settings::compileSdk, settings::maxSdk, settings::targetSdk).filter { it.get() != null }
            val minSdkVersion = settings.minSdk
            for (versionProp in usedVersions) {
                val version = versionProp.get() ?: continue
                if (version >= minSdkVersion) continue
                if (!reportedPlaces.add(versionProp.valueBase?.trace)) continue

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
