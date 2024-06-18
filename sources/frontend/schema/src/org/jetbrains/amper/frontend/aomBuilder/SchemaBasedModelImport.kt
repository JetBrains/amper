/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
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
import org.jetbrains.amper.frontend.catalogs.GradleVersionsCatalogFinder
import org.jetbrains.amper.frontend.diagnostics.AomModelDiagnosticFactories
import org.jetbrains.amper.frontend.processing.readTemplate
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.GradleAmperProjectContext
import org.jetbrains.amper.frontend.project.SingleModuleProjectContextForIde
import org.jetbrains.amper.frontend.project.LegacyAutoDiscoveringProjectContext
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import java.nio.file.Path

// The ServiceLoader mechanism requires a no-arg constructor, which doesn't work with Kotlin objects.
// This proxy allows to provide an instantiable class that delegates everything to the SchemaBasedModelImport object.
internal class SchemaBasedModelImportServiceProxy : ModelInit by SchemaBasedModelImport

object SchemaBasedModelImport : ModelInit {
    override val name = "schema-based"

    context(ProblemReporterContext)
    @Suppress("DEPRECATION")
    @Deprecated("Auto-discovery is deprecated. Use getStandaloneAmperModel() or getGradleAmperModel() instead.")
    @UsedInIdePlugin
    override fun getModel(root: Path, project: Project?): Result<Model> {
        val pathResolver = FrontendPathResolver(project = project)
        val projectContext = LegacyAutoDiscoveringProjectContext(pathResolver.loadVirtualFile(root))
        return getModel(projectContext, pathResolver)
    }

    context(ProblemReporterContext)
    fun getStandaloneAmperModel(projectRootDir: Path, ijProject: Project? = null): Result<Model> {
        val pathResolver = FrontendPathResolver(project = ijProject)
        val projectContext = with(pathResolver) {
            StandaloneAmperProjectContext.create(rootDir = pathResolver.loadVirtualFile(projectRootDir))
                ?: error("Invalid project root")
        }
        return getModel(projectContext, pathResolver)
    }

    context(ProblemReporterContext)
    override fun getGradleAmperModel(rootProjectDir: Path, subprojectDirs: List<Path>): Result<Model> {
        val pathResolver = FrontendPathResolver(project = null)
        val projectContext = GradleAmperProjectContext(
            projectRootDir = pathResolver.loadVirtualFile(rootProjectDir),
            subprojectDirs = subprojectDirs.map { pathResolver.loadVirtualFile(it) },
        )
        return getModel(projectContext, pathResolver)
    }

    context(ProblemReporterContext)
    fun getModel(
        projectContext: AmperProjectContext,
        pathResolver: FrontendPathResolver = FrontendPathResolver(project = null),
    ): Result<Model> {
        val resultModules = doBuild(pathResolver, projectContext)
            ?: return amperFailure()
        val model = DefaultModel(resultModules)
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
    @UsedInIdePlugin
    fun getModule(modulePsiFile: PsiFile, project: Project): Result<PotatoModule> {
        val projectRootDir = requireNotNull(project.guessProjectDir()) { "Project doesn't have base directory" }
        val projectContext = SingleModuleProjectContextForIde(modulePsiFile.virtualFile, projectRootDir)
        val pathResolver = FrontendPathResolver(project = project)
        val resultModules = doBuild(pathResolver, projectContext)
            ?: return amperFailure()
        return resultModules.singleOrNull()?.asAmperSuccess()
            ?: return amperFailure()
    }

    /**
     * Processes the given [templatePsiFile] and reports issues via the [ProblemReporterContext].
     */
    context(ProblemReporterContext)
    @UsedInIdePlugin
    fun getTemplate(templatePsiFile: PsiFile, project: Project): ModelInit.TemplateHolder? {
        val projectRootDir = requireNotNull(project.guessProjectDir()) { "Project doesn't have base directory" }
        val catalogFinder = GradleVersionsCatalogFinder(projectRootDir)
        return with(FrontendPathResolver(project = project)) {
            readTemplate(catalogFinder, templatePsiFile.virtualFile)
        }
    }
}
