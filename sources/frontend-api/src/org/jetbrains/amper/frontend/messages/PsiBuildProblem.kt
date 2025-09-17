/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.messages

import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.createSmartPointer
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.DerivedValueTrace
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.BuildProblemSource
import org.jetbrains.amper.problems.reporting.BuildProblemType
import org.jetbrains.amper.problems.reporting.Level
import java.nio.file.Path
import kotlin.reflect.KProperty0

abstract class PsiBuildProblem(
    override val level: Level,
    override val type: BuildProblemType,
) : BuildProblem {
    abstract val element: PsiElement
    override val source: BuildProblemSource by lazy { PsiBuildProblemSource(element.createSmartPointer()) }
}

fun KProperty0<*>.extractPsiElement(): PsiElement = schemaDelegate.extractPsiElement()

@UsedInIdePlugin
fun KProperty0<*>.extractPsiElementOrNull(): PsiElement? = schemaDelegate.extractPsiElementOrNull()

fun Trace.extractPsiElementOrNull(): PsiElement? = when(this) {
    is PsiTrace -> psiElement
    is DefaultTrace -> null
    is BuiltinCatalogTrace -> version.extractPsiElementOrNull()
    is DerivedValueTrace -> definitionTrace.extractPsiElementOrNull() ?: sourceValue.extractPsiElementOrNull()
}

fun Traceable.extractPsiElementOrNull(): PsiElement? = trace.extractPsiElementOrNull()

fun Traceable.extractPsiElement(): PsiElement =
    extractPsiElementOrNull() ?: error("Can't extract PSI element from traceable $this")

fun Trace.extractPsiElement(): PsiElement =
    extractPsiElementOrNull() ?: error("Can't extract PSI element from trace $this")

val PsiElement.originalFilePath: Path?
    get() = when (this) {
        is PsiFile -> originalFile.virtualFile?.toNioPathOrNull()
        is PsiDirectory -> virtualFile.toNioPathOrNull()
        else -> containingFile.originalFilePath
    }