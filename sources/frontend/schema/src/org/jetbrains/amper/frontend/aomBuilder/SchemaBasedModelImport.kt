/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

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
import org.jetbrains.amper.frontend.processing.validateSchema
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.convertTemplate
import java.nio.file.Path

class SchemaBasedModelImport : ModelInit {
    override val name = "schema-based"

    context(ProblemReporterContext)
    override fun getModel(root: Path): Result<Model> {
        val fioCtx = DefaultFioContext(root)
        val pathResolver = FrontendPathResolver()
        val resultModules = doBuild(pathResolver, fioCtx,)
            ?: return amperFailure()
        // Propagate parts from fragment to fragment.
        return DefaultModel(resultModules + fioCtx.gradleModules.values).resolved.asAmperSuccess()
    }

    context(ProblemReporterContext)
    override fun getModule(modulePsiFile: PsiFile, project: Project): Result<PotatoModule> {
        val fioCtx = ModuleFioContext(modulePsiFile.virtualFile.toNioPath(), project)
        val pathResolver = FrontendPathResolver(project = project)
        val resultModules = doBuild(pathResolver, fioCtx,)
            ?: return amperFailure()
        // Propagate parts from fragment to fragment.
        return resultModules.takeIf { it.size == 1 }?.first()?.withResolvedParts?.asAmperSuccess()
            ?: return amperFailure()
    }

    context(ProblemReporterContext)
    override fun getTemplate(templatePsiFile: PsiFile, project: Project): Result<Unit> {
        val pathResolver = FrontendPathResolver(project = project)
        val templatePath = templatePsiFile.virtualFile.toNioPath()
        with(ConvertCtx(templatePath.parent, pathResolver)) {
            convertTemplate(templatePath)
        }
            ?.validateSchema()
            ?: return amperFailure()
        // Propagate parts from fragment to fragment.
        return Result.success(Unit)
    }

    companion object {
        context(ProblemReporterContext)
        @UsedInIdePlugin
        fun getModel(root: Path): Result<Model> = SchemaBasedModelImport().getModel(root)

        /**
         * @return Module parsed from file with all templates resolved
         */
        context(ProblemReporterContext)
        @UsedInIdePlugin
        fun getModule(modulePsiFile: PsiFile, project: Project): Result<PotatoModule> = SchemaBasedModelImport().getModule(modulePsiFile, project)

        /**
         * @return Module parsed from file with all templates resolved
         */
        context(ProblemReporterContext)
        @UsedInIdePlugin
        fun getTemplate(templatePsiFile: PsiFile, project: Project): Result<Unit> = SchemaBasedModelImport().getTemplate(templatePsiFile, project)
    }
}