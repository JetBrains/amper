/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_LESS_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_MORE_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.SAME

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