/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.reportEmptyModule
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.Template

// TODO Rethink.
internal interface ConvertCtx {
    /**
     * The base directory against which relative paths should be resolved.
     * This is usually the containing directory of the file being resolved.
     */
    val baseFile: VirtualFile
    val pathResolver: FrontendPathResolver
}

internal interface Converter: ProblemReporterContext, ConvertCtx

class ConverterImpl(
    override val baseFile: VirtualFile,
    override val pathResolver: FrontendPathResolver,
    override val problemReporter: ProblemReporter
) : Converter {
    internal fun convertProject(file: VirtualFile): Project? =
        pathResolver.toPsiFile(file)?.doConvertTopLevelValue {
            it?.asMappingNode()?.convertProject()
        }

    internal fun convertModule(file: VirtualFile): Module? =
        pathResolver.toPsiFile(file)?.doConvertTopLevelValue {
            val module = it?.asMappingNode()?.convertModule()
            if (module == null) {
                reportEmptyModule(file)
            }
            module
        }

    internal fun convertCustomTask(file: VirtualFile): CustomTaskNode? =
        pathResolver.toPsiFile(file)?.doConvertTopLevelValue {
            it?.asMappingNode()?.convertCustomTask()
        }

    internal fun convertTemplate(file: VirtualFile): Template? =
        pathResolver.toPsiFile(file)?.doConvertTopLevelValue {
            it?.asMappingNode()?.convertTemplate()
        }

    private fun <T: SchemaNode> PsiFile?.doConvertTopLevelValue(conversion: (PsiElement?) -> T?): T? {
        return ApplicationManager.getApplication().runReadAction(Computable {
            this?.let { conversion(it.topLevelValue) }
        })
    }
}