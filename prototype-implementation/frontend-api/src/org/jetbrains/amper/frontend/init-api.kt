/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.deftFailure
import org.jetbrains.amper.core.flatMap
import org.jetbrains.amper.core.messages.ProblemReporterContext
import java.nio.file.Path
import java.util.*

interface ModelInit {
    companion object {
        const val MODEL_NAME_ENV = "DEFT_MODEL_TYPE"

        const val MODEL_NAME_PROPERTY = "org.jetbrains.deft.model.type"

        context(ProblemReporterContext)
        @UsedInIdePlugin
        fun loadModelInitService(): Result<ModelInit> {
            val services = ServiceLoader.load(ModelInit::class.java).associateBy { it.name }
            if (services.isEmpty()) {
                problemReporter.reportError(FrontendApiBundle.message("no.model.init.service.found"))
                return deftFailure()
            }

            val modelName = System.getProperty(MODEL_NAME_PROPERTY)
                ?: System.getenv(MODEL_NAME_ENV)
                ?: "plain"
            val service = services[modelName]
            return if (service == null) {
                problemReporter.reportError(FrontendApiBundle.message("model.not.found", modelName))
                deftFailure()
            } else {
                Result.success(service)
            }
        }

        context(ProblemReporterContext)
        fun getModel(root: Path): Result<Model> {
            return loadModelInitService().flatMap { it.getModel(root) }
        }
    }

    /**
     * A way to distinguish different models.
     */
    val name: String

    context(ProblemReporterContext)
    fun getModel(root: Path): Result<Model>
}
