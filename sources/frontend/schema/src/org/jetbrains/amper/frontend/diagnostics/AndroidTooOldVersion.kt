/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.ismVisitor.IsmVisitor
import org.jetbrains.amper.frontend.ismVisitor.accept
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.AndroidSettings
import org.jetbrains.amper.frontend.schema.AndroidVersion
import org.jetbrains.amper.frontend.schema.Module

object AndroidTooOldVersion : IsmDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "too.old.android.version"

    context(ProblemReporterContext) override fun Module.analyze() {
        accept(MyVisitor())
    }

    context(ProblemReporterContext)
    private class MyVisitor : IsmVisitor {
        override fun visitAndroidSettings(settings: AndroidSettings) {
            val usedVersions = listOf(settings::compileSdk, settings::minSdk, settings::maxSdk, settings::targetSdk)
            val oldVersions = usedVersions.filter { it.get() < AndroidVersion.VERSION_21 }
            oldVersions.forEach { versionProp ->
                SchemaBundle.reportBundleError(
                    versionProp,
                    diagnosticId,
                    versionProp.get().versionNumber,
                )
            }
        }
    }
}