/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.core.messages.BuildProblemId
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.aomBuilder.chooseComposeVersion
import org.jetbrains.amper.frontend.api.withoutDefault
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.commonSettings

object InconsistentComposeVersion : AomDiagnosticFactory {
    override val diagnosticId: BuildProblemId = "inconsistent.compose.versions"

    context(ProblemReporterContext) override fun Model.analyze() {
        val chosenComposeVersionForModel = chooseComposeVersion(this) ?: return

        val mismatchedComposeSettings = modules.map { it.origin.commonSettings.compose }
            .filter { it.version != null && it.version != chosenComposeVersionForModel }

        mismatchedComposeSettings.forEach {
            SchemaBundle.reportBundleError(
                property = if (it::version.withoutDefault != null) it::version else it::enabled,
                messageKey = diagnosticId,
                chosenComposeVersionForModel,
                level = Level.Fatal,
            )
        }
    }
}