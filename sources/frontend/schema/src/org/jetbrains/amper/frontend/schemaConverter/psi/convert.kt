/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.Template

// TODO Rethink.
internal data class ConvertCtx(
    /**
     * The base directory against which relative paths should be resolved.
     * This is usually the containing directory of the file being resolved.
     */
    val baseFile: VirtualFile,
    val pathResolver: FrontendPathResolver
)

context(ProblemReporterContext, ConvertCtx)
internal fun convertProject(file: VirtualFile): Project? = pathResolver.toPsiFile(file)?.let { convertProjectPsi(it) }

context(ProblemReporterContext, ConvertCtx)
internal fun convertModule(file: VirtualFile): Module? = pathResolver.toPsiFile(file)?.let { convertModulePsi(it) }

context(ProblemReporterContext, ConvertCtx)
internal fun convertCustomTask(file: VirtualFile): CustomTaskNode? = pathResolver.toPsiFile(file)?.let { convertCustomTasksPsi(it) }

context(ProblemReporterContext, ConvertCtx)
internal fun convertTemplate(file: VirtualFile): Template? = pathResolver.toPsiFile(file)?.let { convertTemplatePsi(it) }

context(ProblemReporterContext)
private fun convertProjectPsi(file: PsiFile): Project? {
    // TODO Add reporting.
    return ApplicationManager.getApplication().runReadAction(Computable {
        file.topLevelValue?.convertProject()
    })
}

context(ProblemReporterContext, ConvertCtx)
private fun convertModulePsi(file: PsiFile): Module? {
    // TODO Add reporting.
    return ApplicationManager.getApplication().runReadAction(Computable {
        file.topLevelValue?.convertModule()
    })
}

context(ProblemReporterContext, ConvertCtx)
private fun convertCustomTasksPsi(file: PsiFile): CustomTaskNode? {
    return ApplicationManager.getApplication().runReadAction(Computable {
        file.topLevelValue?.convertCustomTask()
    })
}

context(ProblemReporterContext, ConvertCtx)
private fun convertTemplatePsi(file: PsiFile): Template? {
    // TODO Add reporting.
    return ApplicationManager.getApplication().runReadAction(Computable {
        file.topLevelValue?.convertTemplate()
    })
}
