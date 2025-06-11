/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_LESS_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_MORE_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.SAME


val DefaultCtxs = listOf(DefaultCtx)
data object DefaultCtx : Context {
    override val trace: Trace? = null
    override fun withoutTrace() = DefaultCtx
}

/**
 * Interpret contexts as paths and make inheritance conclusion based on [order] order.
 */
object DefaultInheritance : ContextsInheritance<DefaultCtx> {
    override fun Collection<DefaultCtx>.isMoreSpecificThan(other: Collection<DefaultCtx>): ContextsInheritance.Result {
        val thisIsDefault = this.isNotEmpty()
        val otherIsDefault = other.isNotEmpty()
        return when {
            thisIsDefault == otherIsDefault -> SAME
            thisIsDefault -> IS_LESS_SPECIFIC
            else -> IS_MORE_SPECIFIC
        }
    }
}