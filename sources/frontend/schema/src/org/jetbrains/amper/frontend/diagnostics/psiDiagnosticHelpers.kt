/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.diagnostics

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.doBuild
import org.jetbrains.amper.frontend.processing.readTemplate
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext

/**
 * Analyzes a single [moduleFile] from the Amper project defined by the given [projectContext], to get diagnostics via
 * the given [problemReporter].
 *
 * This takes into account templates, custom tasks, and version catalog entries referenced from [moduleFile].
 */
@UsedInIdePlugin
fun diagnoseAmperModuleFile(
    moduleFile: PsiFile,
    problemReporter: ProblemReporter,
    projectContext: AmperProjectContext,
) {
    val singleModuleContext = projectContext.asSingleModule(moduleFile = moduleFile.virtualFile)
    with(SimpleProblemReporterContext(problemReporter)) {
        // doBuild takes care of ISM diagnostics and single-module diagnostics
        doBuild(singleModuleContext)
    }
}

private fun AmperProjectContext.asSingleModule(moduleFile: VirtualFile): AmperProjectContext {
    val original = this
    return object : AmperProjectContext by original {
        override val amperModuleFiles: List<VirtualFile> = listOf(moduleFile)
        override val amperCustomTaskFiles: List<VirtualFile> =
            original.amperCustomTaskFiles.filter { it.parent == moduleFile.parent }
    }
}

/**
 * Analyzes a single [templateFile] from an Amper project to get diagnostics via the given [problemReporter].
 *
 * This takes into account version catalog entries that are referenced from [templateFile].
 * The project root is guessed from the IntelliJ project associated to the PSI file.
 */
@UsedInIdePlugin
fun diagnoseAmperTemplateFile(
    templateFile: PsiFile,
    problemReporter: ProblemReporter,
    projectContext: AmperProjectContext,
) {
    with(FrontendPathResolver(project = templateFile.project)) {
        with(SimpleProblemReporterContext(problemReporter)) {
            readTemplate(projectContext, templateFile.virtualFile)
        }
    }
}

/**
 * Analyzes the [projectFile] of an Amper project to get diagnostics via the given [problemReporter].
 */
@UsedInIdePlugin
fun diagnoseAmperProjectFile(projectFile: PsiFile, problemReporter: ProblemReporter) {
    with(SimpleProblemReporterContext(problemReporter)) {
        StandaloneAmperProjectContext.create(
            rootDir = projectFile.virtualFile.parent,
            frontendPathResolver = FrontendPathResolver(project = projectFile.project),
        )
    }
}

private class SimpleProblemReporterContext(override val problemReporter: ProblemReporter) : ProblemReporterContext
