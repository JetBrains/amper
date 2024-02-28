/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.messages

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblemSource
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.valueBase
import kotlin.reflect.KProperty0

abstract class PsiBuildProblem(override val level: Level) : BuildProblem {
    abstract val element: PsiElement
    override val source: BuildProblemSource by lazy { PsiBuildProblemSource(element) }
}

fun KProperty0<*>.extractPsiElement(): PsiElement {
    val valueBase = valueBase
    checkNotNull(valueBase) { "Can't extract PSI element from property $name: it doesn't have ValueBase delegate" }
    return valueBase.extractPsiElement()
}

@UsedInIdePlugin
fun KProperty0<*>.extractPsiElementOrNull(): PsiElement? {
    return valueBase?.extractPsiElementOrNull()
}

fun Traceable.extractPsiElement(): PsiElement {
    val trace = trace
    check(trace is PsiTrace) { "Can't extract PSI element from traceable ${this}. Expected to have PSI trace, but has ${trace?.javaClass}" }
    return trace.psiElement
}

fun Traceable.extractPsiElementOrNull(): PsiElement? {
    return (trace as? PsiTrace)?.psiElement
}