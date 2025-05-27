/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.INDETERMINATE
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_LESS_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_MORE_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.SAME


interface Context {
    val trace: Trace?
    fun withoutTrace(): Context
}

typealias Contexts = Collection<Context>

interface WithContexts {
    val contexts: Contexts
}

val EmptyContexts : Contexts = emptyList()

fun interface ContextsInheritance {

    enum class Result {
        IS_MORE_SPECIFIC,
        IS_LESS_SPECIFIC,
        SAME,
        INDETERMINATE,
    }

    fun Contexts.isMoreSpecificThan(other: Contexts): Result
}

operator fun ContextsInheritance.plus(other: ContextsInheritance) = ContextsInheritance {
    val firstResult = isMoreSpecificThan(it)
    val secondResult = with(other) { this@ContextsInheritance.isMoreSpecificThan(it) }
    when {
        firstResult == SAME -> secondResult
        secondResult == SAME -> firstResult
        firstResult != secondResult -> INDETERMINATE
        else -> firstResult
    }
}

val Result.sameOrMoreSpecific
    get() = this == IS_MORE_SPECIFIC || this == SAME

val Result.asCompareResult
    get() = when (this) {
        IS_MORE_SPECIFIC -> 1
        IS_LESS_SPECIFIC -> -1
        SAME -> 0
        INDETERMINATE -> null
    }