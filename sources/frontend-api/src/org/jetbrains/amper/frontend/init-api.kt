/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.messages.GlobalBuildProblemSource
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NonIdealDiagnostic
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.frontend.schema.Template
import java.util.*

interface ModelInit {
    companion object {
        const val MODEL_NAME_ENV = "AMPER_MODEL_TYPE"

        const val MODEL_NAME_PROPERTY = "org.jetbrains.amper.model.type"

        context(problemReporter: ProblemReporter)
        @OptIn(NonIdealDiagnostic::class)
        private fun load(loader: ClassLoader): Result<ModelInit> {
            val services = ServiceLoader.load(ModelInit::class.java, loader).associateBy { it.name }
            if (services.isEmpty()) {
                problemReporter.reportBundleError(
                    source = GlobalBuildProblemSource,
                    messageKey = "no.model.init.services.found",
                    bundle = FrontendApiBundle,
                    level = Level.Fatal,
                )
                return amperFailure()
            }

            val modelName = System.getProperty(MODEL_NAME_PROPERTY)
                ?: System.getenv(MODEL_NAME_ENV)
                ?: "schema-based"

            val service = services[modelName]
            return if (service == null) {
                problemReporter.reportBundleError(
                    source = GlobalBuildProblemSource,
                    messageKey = "model.not.found",
                    modelName,
                    bundle = FrontendApiBundle,
                    level = Level.Fatal,
                )
                amperFailure()
            } else {
                Result.success(service)
            }
        }
    }

    /**
     * A way to distinguish different models.
     */
    val name: String

    /**
     * Wrapper class to hold info about requested template.
     */
    data class TemplateHolder(
        val template: Template,
        @UsedInIdePlugin
        val chosenCatalog: VersionCatalog?,
    )
}
