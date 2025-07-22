/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.asAmperSuccess
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.catalogs.GradleVersionsCatalogFinder
import org.jetbrains.amper.frontend.catalogs.IncorrectCatalogDetection
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.SingleModuleProjectContextForIde

// The ServiceLoader mechanism requires a no-arg constructor, which doesn't work with Kotlin objects.
// This proxy allows to provide an instantiable class that delegates everything to the SchemaBasedModelImport object.
internal class SchemaBasedModelImportServiceProxy : ModelInit by SchemaBasedModelImport

object SchemaBasedModelImport : ModelInit {
    override val name = "schema-based"

    context(problemReporter: ProblemReporter)
    @Deprecated(
        message = "This method is superseded by the simpler AmperProjectContext.readProjectModel().",
        replaceWith = ReplaceWith(
            expression = "projectContext.readProjectModel()?.asAmperSuccess() ?: amperFailure()",
            imports = [
                "org.jetbrains.amper.core.amperFailure",
                "org.jetbrains.amper.core.asAmperSuccess",
                "org.jetbrains.amper.frontend.aomBuilder.readProjectModel",
            ],
        ),
    )
    @UsedInIdePlugin
    fun getModel(projectContext: AmperProjectContext): Result<Model> =
        projectContext.readProjectModel()?.asAmperSuccess() ?: amperFailure()

    /**
     * Creates an [AmperProjectContext] starting from the given [modulePsiFile], reads the project model,
     * and gets the [AmperModule] that was read from the given [modulePsiFile] in this model.
     */
    context(problemReporter: ProblemReporter)
    @IncorrectCatalogDetection
    @Deprecated(
        message = "This returns a partially incorrect module with unresolved references to other modules. " +
                "Also, custom tasks and version catalog references might be incorrect. " +
                "Prefer using a real project context, reading the full model, and selecting the module.",
        replaceWith = ReplaceWith(
            expression = "StandaloneAmperProjectContext.find(modulePsiFile.virtualFile, project)" +
                    "?.readProjectModel()" +
                    "?.getModule(modulePsiFile.virtualFile)" +
                    "?.asAmperSuccess()" +
                    " ?: amperFailure()",
            imports = [
                "org.jetbrains.amper.core.amperFailure",
                "org.jetbrains.amper.core.asAmperSuccess",
                "org.jetbrains.amper.frontend.aomBuilder.readProjectModel",
                "org.jetbrains.amper.frontend.getModule",
                "org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext",
            ],
        ),
    )
    @UsedInIdePlugin
    fun getModule(modulePsiFile: PsiFile, project: Project): Result<AmperModule> {
        val pathResolver = FrontendPathResolver(project = project)
        val projectContext = SingleModuleProjectContextForIde(modulePsiFile.virtualFile, pathResolver)
        val resultModules = doBuild(projectContext)
            ?: return amperFailure()
        return resultModules.singleOrNull()?.asAmperSuccess()
            ?: return amperFailure()
    }

    /**
     * Processes the given [templatePsiFile] and reports issues via the [ProblemReporter].
     */
    context(problemReporter: ProblemReporter)
    @IncorrectCatalogDetection
    @Deprecated(
        message = "This returns a template with potentially incorrect version catalog references." +
                "Prefer using a real project context. If you're only interested in the version catalog, " +
                "use AmperProjectContext.readEffectiveCatalogForTemplate(templateFile).",
    )
    @UsedInIdePlugin
    fun getTemplate(templatePsiFile: PsiFile, project: Project): ModelInit.TemplateHolder? =
        with(FrontendPathResolver(project = project)) {
            val catalog = GradleVersionsCatalogFinder.findGradleVersionCatalogUpTheTreeFrom(templatePsiFile.virtualFile)
            readTemplate(templateFile = templatePsiFile.virtualFile, projectVersionCatalog = catalog)
        }
}
