/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.hasPublishingConfigured
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

object PublishingSettingsMissingInDependencies : AomModelDiagnosticFactory {

    override fun analyze(model: Model, problemReporter: ProblemReporter) {
        model.modules.forEach { module ->
            if (module.hasPublishingConfigured()) {
                module.fragments
                    .filterNot { it.isTest }
                    .flatMap { it.externalDependencies }
                    .filterIsInstance<LocalModuleDependency>()
                    .filterNot { it.module.hasPublishingConfigured() }
                    .forEach { dep ->
                        problemReporter.reportBundleError(
                            source = dep.asBuildProblemSource(),
                            messageKey = "published.module.0.depends.on.non.published.module.1",
                            module.userReadableName,
                            dep.module.userReadableName,
                            level = Level.Error,
                            problemType = BuildProblemType.InconsistentConfiguration,
                        )
                    }
            }
        }
    }
}
