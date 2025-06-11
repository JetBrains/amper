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


/**
 * A context represents the specificity of the value in the `TreeValue`
 * that affects how this value will be refined (see `TreeRefiner`).
 *
 * Each subclass should represent a new "dimension" of possible contexts.
 * For example, `PlatformContext` represents the platforms for which some value is set.
 *
 * Each context "dimension" contains "null" context implicitly,
 * which means that there is no context of that dimension in `Contexts` for the value.
 */
interface Context {
    val trace: Trace?
    fun withoutTrace(): Context
}

/**
 * Set of contexts that are applied to the value.
 * Contexts from different dimensions may be present here.
 */
typealias Contexts = Collection<Context>

interface WithContexts {
    val contexts: Contexts
}

val EmptyContexts : Contexts = emptyList()

/**
 * Declares an inheritance relation between two sets of contexts for the provided dimension.
 */
fun interface ContextsInheritance<T : Context> {

    enum class Result {
        IS_MORE_SPECIFIC,
        IS_LESS_SPECIFIC,
        SAME,
        INDETERMINATE,
    }

    fun Collection<T>.isMoreSpecificThan(other: Collection<T>): Result
}

/**
 * A way to combine two context inheritance relations and make a generic one.
 */
inline operator fun <reified F : Context, reified S : Context> ContextsInheritance<F>.plus(
    other: ContextsInheritance<S>
) = ContextsInheritance<Context> {
    val firstResult = filterIsInstance<F>().isMoreSpecificThan(it.filterIsInstance<F>())
    val secondResult = with(other) { filterIsInstance<S>().isMoreSpecificThan(it.filterIsInstance<S>()) }
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