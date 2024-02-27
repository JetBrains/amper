/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.ismVisitor.IsmVisitor
import org.jetbrains.amper.frontend.ismVisitor.accept
import org.jetbrains.amper.frontend.messages.PsiBuildProblem
import org.jetbrains.amper.frontend.messages.extractPsiElement
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidVersion
import org.jetbrains.amper.frontend.schema.Module
import kotlin.reflect.KProperty0

class AndroidVersionShouldBeAtLeastMinSdk(
    @UsedInIdePlugin
    val versionProp: KProperty0<AndroidVersion>,
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
            versionProp.get().versionNumber,
            minSdkVersion.versionNumber
        )
}

object AndroidVersionShouldBeAtLeastMinSdkFactory : IsmDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "android.version.should.be.at.least.min.sdk"

    context(ProblemReporterContext) override fun Module.analyze() {
        accept(MyVisitor())
    }

    context(ProblemReporterContext)
    private class MyVisitor : IsmVisitor {
        override fun visitAndroidSettings(settings: AndroidSettings) {
            val usedVersions = listOf(settings::compileSdk, settings::maxSdk, settings::targetSdk)
            val smallerVersions = usedVersions.filter { it.get() < settings.minSdk }
            smallerVersions.forEach { versionProp ->
                problemReporter.reportMessage(
                    AndroidVersionShouldBeAtLeastMinSdk(
                        versionProp,
                        minSdkVersion = settings.minSdk,
                    )
                )
            }
        }
    }
}
