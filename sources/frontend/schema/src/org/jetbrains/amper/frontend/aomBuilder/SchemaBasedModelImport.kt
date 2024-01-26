/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.asAmperSuccess
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.processing.readTemplate
import org.jetbrains.amper.frontend.schemaConverter.psi.standalone.DummyProject
import java.nio.file.Path

class SchemaBasedModelImport : ModelInit {
    override val name = "schema-based"

    context(ProblemReporterContext)
    override fun getModel(root: Path, project: Project?): Result<Model> {
        val fioCtx = DefaultFioContext(root)
        val pathResolver = FrontendPathResolver(project = project ?: DummyProject.instance)
        val resultModules = doBuild(pathResolver, fioCtx)
            ?: return amperFailure()
        // Propagate parts from fragment to fragment.
        return DefaultModel(resultModules + fioCtx.gradleModules.values)
            .resolved
            // Add additional validations for the whole model.
            .performValidations()
            .asAmperSuccess()
    }

    context(ProblemReporterContext)
    override fun getModule(modulePsiFile: PsiFile, project: Project): Result<PotatoModule> {
        val fioCtx = ModuleFioContext(modulePsiFile.virtualFile.toNioPath(), project)
        val pathResolver = FrontendPathResolver(project = project)
        val resultModules = doBuild(pathResolver, fioCtx)
            ?: return amperFailure()
        // Propagate parts from fragment to fragment.
        return resultModules.takeIf { it.size == 1 }?.first()?.withResolvedFragments()?.asAmperSuccess()
            ?: return amperFailure()
    }

    context(ProblemReporterContext)
    override fun getTemplate(
        templatePsiFile: PsiFile,
        project: Project
    ): ModelInit.TemplateHolder? {
        val templatePath = templatePsiFile.virtualFile.toNioPath()
        val fioCtx = ModuleFioContext(templatePath, project)
        return with(FrontendPathResolver(project = project)) {
            readTemplate(fioCtx, templatePath)
        }
    }

    companion object {
        /**
         * @return Project model containing all modules found and referenced from a given root
         */
        context(ProblemReporterContext)
        @UsedInIdePlugin
        @Suppress("UnstableApiUsage")
        suspend fun getModelNonBlocking(root: Path, project: Project?): Result<Model> {
            return blockingContext {
                getModel(root, project)
            }
        }
        context(ProblemReporterContext)
        @UsedInIdePlugin
        fun getModel(root: Path, project: Project?): Result<Model> = SchemaBasedModelImport().getModel(root, project)

        /**
         * @return Module parsed from file with all templates resolved
         */
        context(ProblemReporterContext)
        @UsedInIdePlugin
        @Suppress("UnstableApiUsage")
        suspend fun getModuleNonBlocking(modulePsiFile: PsiFile, project: Project): Result<PotatoModule> = blockingContext {
            getModule(modulePsiFile, project)
        }
        context(ProblemReporterContext)
        @UsedInIdePlugin
        fun getModule(modulePsiFile: PsiFile, project: Project): Result<PotatoModule> =
            SchemaBasedModelImport().getModule(modulePsiFile, project)

        /**
         * @return Module parsed from file with all templates resolved
         */
        context(ProblemReporterContext)
        @UsedInIdePlugin
        @Suppress("UnstableApiUsage")
        suspend fun getTemplateNonBlocking(templatePsiFile: PsiFile, project: Project): ModelInit.TemplateHolder? = blockingContext {
            SchemaBasedModelImport().getTemplate(templatePsiFile, project)
        }
        context(ProblemReporterContext)
        @UsedInIdePlugin
        fun getTemplate(templatePsiFile: PsiFile, project: Project): ModelInit.TemplateHolder? =
            SchemaBasedModelImport().getTemplate(templatePsiFile, project)
    }
}