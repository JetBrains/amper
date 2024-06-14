/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.amper.frontend.diagnostics.AomModelDiagnosticFactories
import org.jetbrains.amper.frontend.processing.readTemplate
import java.nio.file.Path

// The ServiceLoader mechanism requires a no-arg constructor, which doesn't work with Kotlin objects.
// This proxy allows to provide an instantiable class that delegates everything to the SchemaBasedModelImport object.
internal class SchemaBasedModelImportServiceProxy : ModelInit by SchemaBasedModelImport

object SchemaBasedModelImport : ModelInit {
    override val name = "schema-based"

    context(ProblemReporterContext)
    @UsedInIdePlugin
    override fun getModel(root: Path, project: Project?): Result<Model> {
        val pathResolver = FrontendPathResolver(project = project)
        val fioCtx = DefaultFioContext(pathResolver.loadVirtualFile(root))
        val resultModules = doBuild(pathResolver, fioCtx)
            ?: return amperFailure()
        val model = DefaultModel(resultModules + fioCtx.gradleModules.values)
        AomModelDiagnosticFactories.forEach { diagnostic ->
            with(diagnostic) { model.analyze() }
        }
        return model.asAmperSuccess()
    }

    /**
     * @return Module parsed from file with all templates resolved
     */
    context(ProblemReporterContext)
    @UsedInIdePlugin
    fun getModule(modulePsiFile: PsiFile, project: Project): Result<PotatoModule> {
        val fioCtx = ModuleFioContext(modulePsiFile.virtualFile, project)
        val pathResolver = FrontendPathResolver(project = project)
        val resultModules = doBuild(pathResolver, fioCtx)
            ?: return amperFailure()
        return resultModules.singleOrNull()?.asAmperSuccess()
            ?: return amperFailure()
    }

    /**
     * @return Module parsed from file with all templates resolved
     */
    context(ProblemReporterContext)
    @UsedInIdePlugin
    fun getTemplate(templatePsiFile: PsiFile, project: Project): ModelInit.TemplateHolder? {
        val templatePath = templatePsiFile.virtualFile
        val fioCtx = ModuleFioContext(templatePath, project)
        return with(FrontendPathResolver(project = project)) {
            readTemplate(fioCtx, templatePath)
        }
    }
}
