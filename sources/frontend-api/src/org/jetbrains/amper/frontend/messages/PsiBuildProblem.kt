/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.messages

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.BuildProblemSource
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Trace
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

// FIXME Why do we have this?
//fun Traceable.extractPsiElement(): PsiElement =
//    when (val trace = trace) {
//        // FIXME Rethink default traces.
//        is DefaultTrace -> trace.computedValueTrace?.extractPsiElement()
//            ?: error { "Can't extract PSI element from traceable ${this}. Referenced trace is null" }
//
//        is PsiTrace -> trace.psiElement
//
//        // todo (AB) : It is not correct to throw error here, either returned value should be nullable,
//        // todo (AB) : or built-in catalogue entries should be associated with the real trace in some virtual file.
//        is BuiltinCatalogTrace -> error("Can't extract PSI element from traceable ${this}. Expected to have PSI trace, but has ${trace.javaClass}")
//
//        null -> error("Can't extract PSI element from traceable ${this}. Element doesn't have trace")
//    }

fun Trace.extractPsiElementOrNull(): PsiElement? = when(this) {
    is PsiTrace -> psiElement
    else -> computedValueTrace?.extractPsiElementOrNull()
}

fun Traceable.extractPsiElementOrNull(): PsiElement? = 
    trace?.extractPsiElementOrNull()

fun Traceable.extractPsiElement(): PsiElement =
    extractPsiElementOrNull() ?: error("Can't extract PSI element from traceable $this")

fun Trace.extractPsiElement(): PsiElement =
    extractPsiElementOrNull() ?: error("Can't extract PSI element from trace $this")