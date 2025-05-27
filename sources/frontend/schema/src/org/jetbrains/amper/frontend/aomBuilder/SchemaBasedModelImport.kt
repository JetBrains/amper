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
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.catalogs.GradleVersionsCatalogFinder
import org.jetbrains.amper.frontend.diagnostics.AomModelDiagnosticFactories
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.SingleModuleProjectContextForIde
import java.nio.file.Path

// The ServiceLoader mechanism requires a no-arg constructor, which doesn't work with Kotlin objects.
// This proxy allows to provide an instantiable class that delegates everything to the SchemaBasedModelImport object.
internal class SchemaBasedModelImportServiceProxy : ModelInit by SchemaBasedModelImport

object SchemaBasedModelImport : ModelInit {
    override val name = "schema-based"

    context(ProblemReporterContext)
    fun getModel(projectContext: AmperProjectContext): Result<Model> {
        val resultModules = doBuild(projectContext)
            ?: return amperFailure()
        val model = DefaultModel(projectContext.projectRootDir.toNioPath(), resultModules)
        AomModelDiagnosticFactories.forEach { diagnostic ->
            with(diagnostic) { model.analyze() }
        }
        return model.asAmperSuccess()
    }

    // TODO find a better way to do this in the IDE
    /**
     * This is a hack to analyze a single module from a wider project, to get diagnostics in the IDE editor.
     *
     * The returned module is parsed, and all templates resolved, but dependencies on other modules are unresolved.
     * Since this is mostly used for diagnostics reported via the [ProblemReporterContext], the unresolved references
     * are usually ignored.
     */
    context(ProblemReporterContext)
    @Deprecated(
        message = "This returns a partially incorrect module with unresolved references to other modules. " +
            "Also, custom tasks and version catalog references might be incorrect. " +
            "Prefer using diagnoseAmperModuleFile() with a real project context.",
        replaceWith = ReplaceWith(
            expression = "diagnoseAmperModuleFile(modulePsiFile, this@ProblemReporterContext.problemReporter, context)",
            imports = ["org.jetbrains.amper.frontend.diagnostics.diagnoseAmperModuleFile"],
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
     * Processes the given [templatePsiFile] and reports issues via the [ProblemReporterContext].
     */
    context(ProblemReporterContext)
    @Deprecated(
        message = "This returns a template with potentially incorrect version catalog references." +
                "Prefer using diagnoseAmperTemplateFile() with a real project context.",
        replaceWith = ReplaceWith(
            expression = "diagnoseAmperTemplateFile(modulePsiFile, this@ProblemReporterContext.problemReporter, context)",
            imports = ["org.jetbrains.amper.frontend.diagnostics.diagnoseAmperTemplateFile"],
        ),
    )
    @UsedInIdePlugin
    fun getTemplate(templatePsiFile: PsiFile, project: Project): ModelInit.TemplateHolder? {
        return readTemplate(GradleVersionsCatalogFinder(FrontendPathResolver(project = project)), templatePsiFile.virtualFile)
    }
}
