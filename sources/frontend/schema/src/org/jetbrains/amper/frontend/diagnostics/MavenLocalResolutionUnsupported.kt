/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Repository.Companion.SpecialMavenLocalUrl

object MavenLocalResolutionUnsupported : IsmDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "maven.local.resolution.not.supported"

    context(ProblemReporterContext)
    override fun Module.analyze() {
        val resolvableLocalRepo = repositories?.find { it.url == SpecialMavenLocalUrl && it.resolve }
        if (resolvableLocalRepo != null) {
            SchemaBundle.reportBundleError(
                value = resolvableLocalRepo,
                messageKey = "maven.local.resolution.not.supported",
                level = Level.Warning,
            )
        }
    }
}
