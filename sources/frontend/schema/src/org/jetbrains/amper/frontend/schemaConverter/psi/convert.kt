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
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.builders.isMap
import org.jetbrains.amper.frontend.builders.schemaDeclaredMutableProperties
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.Template
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

internal interface Converter: ProblemReporterContext {
    /**
     * The base directory against which relative paths should be resolved.
     * This is usually the containing directory of the file being resolved.
     */
    val baseFile: VirtualFile
    val pathResolver: FrontendPathResolver
}

class ConverterImpl(
    override val baseFile: VirtualFile,
    override val pathResolver: FrontendPathResolver,
    override val problemReporter: ProblemReporter
) : Converter {
    internal fun convertProject(file: VirtualFile): Project? =
        pathResolver.toPsiFile(file)?.doConvertTopLevelValue {
            it?.convert<Project>()
        }

    internal fun convertModule(file: VirtualFile): Module? =
        pathResolver.toPsiFile(file)?.doConvertTopLevelValue {
            val module = it?.convert<Module>()
            if (module == null) {
                reportEmptyModule(file)
            }
            module
        }

    internal fun convertCustomTask(file: VirtualFile): CustomTaskNode? =
        pathResolver.toPsiFile(file)?.doConvertTopLevelValue {
            it?.convert<CustomTaskNode>()
        }

    internal fun convertTemplate(file: VirtualFile): Template? =
        pathResolver.toPsiFile(file)?.doConvertTopLevelValue {
            it?.convert<Template>()
        }

    private fun <T: SchemaNode> PsiFile?.doConvertTopLevelValue(conversion: (PsiElement?) -> T?): T? {
        return ApplicationManager.getApplication().runReadAction(Computable {
            this?.let { conversion(it.topLevelValue) }
        })
    }

    private inline fun <reified T: Any> PsiElement.convert(): T {
        val table = readValueTable()
        val contextToModule = mutableMapOf<Set<TraceableString>, T>()
        val contexts = (table.keys.map { it.contexts } + listOf(emptySet())).distinct()
        contexts.forEach {
            val module: T = T::class.constructors.single().call()
            readFromTable(module, table, contexts = it)
            contextToModule[it] = module
        }
        val module = contextToModule[emptySet()]!!
        for (prop in module::class.schemaDeclaredMutableProperties()) {
            if (prop.returnType.isMap && prop.returnType.arguments.getOrNull(0)?.type?.isSubtypeOf(Set::class.starProjectedType) == true) {
                for (key in contexts.filter { it.isNotEmpty() }) {
                    val moduleToMergeIn = contextToModule[key] ?: continue
                    val mergedValue = prop.valueBase(moduleToMergeIn)?.withoutDefault as? Map<*, *> ?: continue
                    val currentValue = prop.get(module) as? Map<*, *>
                    val initialTrace = if (currentValue != null) prop.valueBase(module)?.trace else prop.valueBase(moduleToMergeIn)?.trace
                    prop.set(module, currentValue.orEmpty() + mergedValue)
                    prop.valueBase(module)?.trace = initialTrace
                }
            }
        }
        return module
    }
}