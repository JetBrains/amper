/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.flatMap
import org.jetbrains.amper.core.messages.BuildProblemImpl
import org.jetbrains.amper.core.messages.GlobalBuildProblemSource
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NonIdealDiagnostic
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.Template
import java.nio.file.Path
import java.util.*

interface ModelInit {
    companion object {
        const val MODEL_NAME_ENV = "AMPER_MODEL_TYPE"

        const val MODEL_NAME_PROPERTY = "org.jetbrains.amper.model.type"

        context(ProblemReporterContext)
        @OptIn(NonIdealDiagnostic::class)
        private fun load(loader: ClassLoader): Result<ModelInit> {
            val services = ServiceLoader.load(ModelInit::class.java, loader).associateBy { it.name }
            if (services.isEmpty()) {
                problemReporter.reportMessage(
                    BuildProblemImpl(
                        buildProblemId = "no.model.init.service.found",
                        source = GlobalBuildProblemSource,
                        message = FrontendApiBundle.message("no.model.init.service.found"),
                        level = Level.Fatal,
                    )
                )
                return amperFailure()
            }

            val modelName = System.getProperty(MODEL_NAME_PROPERTY)
                ?: System.getenv(MODEL_NAME_ENV)
                ?: "schema-based"

            val service = services[modelName]
            return if (service == null) {
                problemReporter.reportMessage(
                    BuildProblemImpl(
                        buildProblemId = "model.not.found",
                        source = GlobalBuildProblemSource,
                        message = FrontendApiBundle.message("model.not.found", modelName),
                        level = Level.Fatal,
                    )
                )
                amperFailure()
            } else {
                Result.success(service)
            }
        }

        /**
         * Initializes an Amper model in the context of a Gradle-based project.
         *
         * @param rootProjectDir The directory of the Gradle root project.
         * @param subprojectDirs The directories of all Gradle subprojects declared in the Gradle settings.
         * @param loader The ClassLoader from which to load the implementation of [ModelInit].
         */
        context(ProblemReporterContext)
        fun getGradleAmperModel(
            rootProjectDir: Path,
            subprojectDirs: List<Path>,
            loader: ClassLoader = Thread.currentThread().contextClassLoader,
        ): Result<Model> {
            return load(loader).flatMap { it.getGradleAmperModel(rootProjectDir, subprojectDirs) }
        }
    }

    /**
     * A way to distinguish different models.
     */
    val name: String

    /**
     * Initializes an Amper model in the context of a Gradle-based project.
     *
     * @param rootProjectDir The directory of the Gradle root project.
     * @param subprojectDirs The directories of all Gradle subprojects declared in the Gradle settings.
     */
    context(ProblemReporterContext)
    fun getGradleAmperModel(rootProjectDir: Path, subprojectDirs: List<Path>): Result<Model>

    /**
     * Wrapper class to hold info about requested template.
     */
    data class TemplateHolder(
        val template: Template,
        @UsedInIdePlugin
        val chosenCatalog: VersionCatalog?,
    )
}
