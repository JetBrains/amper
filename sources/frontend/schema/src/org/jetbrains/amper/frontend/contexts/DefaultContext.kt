/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_LESS_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_MORE_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.SAME

/**
 * Context for the values that are set internally by Amper logic.
 */
sealed class DefaultContext(
    val priority: Int,
) : Context {
    override val trace: Trace? = null
    override fun withoutTrace() = this

    /**
     * Default which is set at the type-level. Has the lowest priority.
     */
    object TypeLevel : DefaultContext(priority = 0) {
        override fun toString() = "DefaultContext.TypeLevel"
    }

    /**
     * Default which is set in *reaction* to some feature/sub-system/... being enabled.
     * Has higher priority than [TypeLevel] default.
     */
    object ReactivelySet : DefaultContext(priority = 1) {
        override fun toString() = "DefaultContext.ReactivelySet"
    }
}

/**
 * Compares DefaultContexts by their [DefaultContext.priority].
 * There can be only one [DefaultContext] per value.
 */
object DefaultInheritance : ContextsInheritance<DefaultContext> {
    private fun Collection<DefaultContext>.priority(): Int {
        check(size <= 1) { "Not more than one default context is permitted per value, but got: $this" }
        return singleOrNull()?.priority ?: Int.MAX_VALUE
    }

    override fun Collection<DefaultContext>.isMoreSpecificThan(
        other: Collection<DefaultContext>,
    ): ContextsInheritance.Result {
        val result = priority() compareTo other.priority()
        return when {
            result == 0 -> SAME
            result < 0 -> IS_LESS_SPECIFIC
            else -> IS_MORE_SPECIFIC
        }
    }
}