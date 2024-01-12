/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.flatMap
import org.jetbrains.amper.core.messages.ProblemReporterContext
import java.nio.file.Path
import java.util.*

interface ModelInit {
    companion object {
        const val MODEL_NAME_ENV = "AMPER_MODEL_TYPE"

        const val MODEL_NAME_PROPERTY = "org.jetbrains.amper.model.type"

        context(ProblemReporterContext)
        @UsedInIdePlugin

        fun loadModelInitService(loader: ClassLoader): Result<ModelInit> {
            val services = ServiceLoader.load(ModelInit::class.java, loader).associateBy { it.name }
            if (services.isEmpty()) {
                problemReporter.reportError(FrontendApiBundle.message("no.model.init.service.found"))
                return amperFailure()
            }

            val modelName = System.getProperty(MODEL_NAME_PROPERTY)
                ?: System.getenv(MODEL_NAME_ENV)
                ?: "schema-based"
//                ?: "plain"
            val service = services[modelName]
            return if (service == null) {
                problemReporter.reportError(FrontendApiBundle.message("model.not.found", modelName))
                amperFailure()
            } else {
                Result.success(service)
            }
        }

        context(ProblemReporterContext)
        fun getModel(root: Path, loader: ClassLoader = Thread.currentThread().contextClassLoader): Result<Model> {
            return loadModelInitService(loader).flatMap { it.getModel(root) }
        }

        context(ProblemReporterContext)
        fun getModule(root: PsiFile, project: Project, loader: ClassLoader = Thread.currentThread().contextClassLoader): Result<PotatoModule> {
            return loadModelInitService(loader).flatMap { it.getModule(root, project) }
        }

        context(ProblemReporterContext)
        fun getTemplate(templatePath: PsiFile, project: Project, loader: ClassLoader = Thread.currentThread().contextClassLoader): Result<Unit> {
            return loadModelInitService(loader).flatMap { it.getTemplate(templatePath, project) }
        }
    }

    /**
     * A way to distinguish different models.
     */
    val name: String

    context(ProblemReporterContext)
    fun getModel(root: Path): Result<Model>

    context(ProblemReporterContext)
    fun getModule(modulePsiFile: PsiFile, project: Project): Result<PotatoModule>

    context(ProblemReporterContext)
    fun getTemplate(templatePsiFile: PsiFile, project: Project): Result<Unit>
}
