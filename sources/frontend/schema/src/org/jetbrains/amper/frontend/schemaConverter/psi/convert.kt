/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.amper.lang.AmperFile
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
import org.jetbrains.amper.frontend.schemaConverter.psi.amper.convertModule
import org.jetbrains.amper.frontend.schemaConverter.psi.amper.convertProject
import org.jetbrains.amper.frontend.schemaConverter.psi.amper.convertTemplate
import org.jetbrains.amper.frontend.schemaConverter.psi.yaml.convertCustomTask
import org.jetbrains.amper.frontend.schemaConverter.psi.yaml.convertModule
import org.jetbrains.amper.frontend.schemaConverter.psi.yaml.convertProject
import org.jetbrains.amper.frontend.schemaConverter.psi.yaml.convertTemplate
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile

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

context(ProblemReporterContext, ConvertCtx)
private fun convertProjectPsi(file: PsiFile): Project? {
    // TODO Add reporting.
    return ApplicationManager.getApplication().runReadAction(Computable {
        when (file) {
            is YAMLFile -> file.children.filterIsInstance<YAMLDocument>().firstOrNull()?.convertProject()
            is AmperFile -> file.convertProject()
            else -> null
        }
    })
}

context(ProblemReporterContext, ConvertCtx)
private fun convertModulePsi(file: PsiFile): Module? {
    // TODO Add reporting.
    return ApplicationManager.getApplication().runReadAction(Computable {
        when (file) {
            is YAMLFile -> file.children.filterIsInstance<YAMLDocument>().firstOrNull()?.convertModule()
            is AmperFile -> file.convertModule()
            else -> null
        }
    })
}

context(ProblemReporterContext, ConvertCtx)
private fun convertCustomTasksPsi(file: PsiFile): CustomTaskNode? {
    return ApplicationManager.getApplication().runReadAction(Computable {
        return@Computable when (file) {
            is YAMLFile -> file.children.filterIsInstance<YAMLDocument>().firstOrNull()?.convertCustomTask()
            is AmperFile -> throw UnsupportedOperationException("Amper files are not yet supported")
            else -> null
        }
    })
}

context(ProblemReporterContext, ConvertCtx)
private fun convertTemplatePsi(file: PsiFile): Template? {
    // TODO Add reporting.
    return ApplicationManager.getApplication().runReadAction(Computable {
        when (file) {
            is YAMLFile -> file.children.filterIsInstance<YAMLDocument>().firstOrNull()?.convertTemplate()
            is AmperFile -> file.convertTemplate()
            else -> null
        }
    })
}
