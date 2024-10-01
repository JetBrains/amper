/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.psi.PsiElement
import org.jetbrains.amper.frontend.customTaskSchema.CustomTaskNode
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.frontend.schema.Template

context(Converter)
internal fun PsiElement.convertProject() = doConvert<Project>()

context(Converter)
internal fun PsiElement.convertTemplate() = doConvert<Template>()

context(Converter)
internal fun PsiElement.convertModule() = doConvert<Module>()

context(Converter)
internal fun PsiElement.convertCustomTask() = doConvert<CustomTaskNode>()

context(Converter)
private inline fun <reified T: Any> PsiElement.doConvert(): T {
    val table = readValueTable()
    val module: T = T::class.constructors.single().call()
    readFromTable(module, table)
    return module
}