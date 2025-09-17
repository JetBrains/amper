/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.messages

import com.intellij.openapi.vfs.originalFileOrSelf
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
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

@Deprecated(
    "If a build problem really wants to expose PSI element, it should be done via source sub-typing into PsiBuildProblemSource. " +
        "Holding references to PSI elements can lead to memory leaks and inconsistencies inside IDE. At the same time" +
            "it's fine to just expose the BuildProblemSource and let the IDE figure out the element from it."
)
abstract class PsiBuildProblem(
    override val level: Level,
    override val type: BuildProblemType,
) : BuildProblem {
    @Deprecated(
        "Use source property instead, dereference the element and properly process `null` value",
        replaceWith = ReplaceWith("(source as PsiBuildProblemSource).psiPointer.element ?: error(\"No PSI element found\")")
    )
    abstract val element: PsiElement
    override val source: BuildProblemSource by lazy { PsiBuildProblemSource(element.createSmartPointer()) }
}

/**
 * Do not store extracted [PsiElement] in any objects as it might lead to memory leaks and inconsistencies inside IDE.
 */
fun KProperty0<*>.extractPsiElement(): PsiElement = schemaDelegate.extractPsiElement()

/**
 * Do not store extracted [PsiElement] in any objects as it might lead to memory leaks and inconsistencies inside IDE.
 */
@UsedInIdePlugin
fun KProperty0<*>.extractPsiElementOrNull(): PsiElement? = schemaDelegate.extractPsiElementOrNull()

/**
 * Do not store extracted [PsiElement] in any objects as it might lead to memory leaks and inconsistencies inside IDE.
 */
fun Trace.extractPsiElementOrNull(): PsiElement? = when(this) {
    is PsiTrace -> psiPointer.element
    is DefaultTrace -> null
    is BuiltinCatalogTrace -> version.extractPsiElementOrNull()
    is DerivedValueTrace -> definitionTrace.extractPsiElementOrNull() ?: sourceValue.extractPsiElementOrNull()
}

/**
 * Do not store extracted [PsiElement] in any objects as it might lead to memory leaks and inconsistencies inside IDE.
 */
fun Traceable.extractPsiElementOrNull(): PsiElement? = trace.extractPsiElementOrNull()

/**
 * Do not store extracted [PsiElement] in any objects as it might lead to memory leaks and inconsistencies inside IDE.
 */
fun Traceable.extractPsiElement(): PsiElement =
    extractPsiElementOrNull() ?: error("Can't extract PSI element from traceable $this")

/**
 * Do not store extracted [PsiElement] in any objects as it might lead to memory leaks and inconsistencies inside IDE.
 */
fun Trace.extractPsiElement(): PsiElement =
    extractPsiElementOrNull() ?: error("Can't extract PSI element from trace $this")

val SmartPsiElementPointer<*>.originalFilePath: Path?
    get() = virtualFile.originalFileOrSelf().toNioPathOrNull()