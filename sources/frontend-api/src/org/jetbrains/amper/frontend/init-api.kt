/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.flatMap
import org.jetbrains.amper.core.getOrNull
import org.jetbrains.amper.core.map
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
        private fun loadModelInitService(loader: ClassLoader): Result<ModelInit> {
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

        context(ProblemReporterContext)
        fun getModel(root: Path, loader: ClassLoader = Thread.currentThread().contextClassLoader): Result<Model> {
            return loadModelInitService(loader).flatMap { it.getModel(root, project = null) }
        }

        context(ProblemReporterContext)
        fun getModel(root: Path, project: Project? = null, loader: ClassLoader = Thread.currentThread().contextClassLoader): Result<Model> {
            return loadModelInitService(loader).flatMap { it.getModel(root, project) }
        }

        context(ProblemReporterContext)
        fun getModule(root: PsiFile, project: Project, loader: ClassLoader = Thread.currentThread().contextClassLoader): Result<PotatoModule> {
            return loadModelInitService(loader).flatMap { it.getModule(root, project) }
        }

        context(ProblemReporterContext)
        fun getTemplate(
            templatePath: PsiFile,
            project: Project,
            loader: ClassLoader = Thread.currentThread().contextClassLoader
        ): TemplateHolder? = loadModelInitService(loader)
            .map { it.getTemplate(templatePath, project) }
            .getOrNull()
    }

    /**
     * A way to distinguish different models.
     */
    val name: String

    context(ProblemReporterContext)
    fun getModel(root: Path, project: Project?): Result<Model>

    context(ProblemReporterContext)
    fun getModule(modulePsiFile: PsiFile, project: Project): Result<PotatoModule>

    /**
     * Wrapper class to hold info about requested template.
     */
    data class TemplateHolder(
        val template: Template,
        @UsedInIdePlugin
        val chosenCatalog: VersionCatalog?,
    )

    context(ProblemReporterContext)
    fun getTemplate(templatePsiFile: PsiFile, project: Project): TemplateHolder?
}
