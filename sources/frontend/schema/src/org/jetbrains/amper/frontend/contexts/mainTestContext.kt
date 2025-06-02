/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_LESS_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_MORE_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.SAME


open class TestCtx(override val trace: Trace?) : Context {
    companion object : TestCtx(null)
    override fun withoutTrace() = TestCtx
    override fun toString() = "test"
}

/**
 * Extract tests contexts if any and treat non containing these as less specific.
 */
object MainTestInheritance : ContextsInheritance {
    override fun Contexts.isMoreSpecificThan(other: Contexts): ContextsInheritance.Result {
        val thisIsTest = this.any { it is TestCtx }
        val otherIsTest = other.any { it is TestCtx }
        return when {
            thisIsTest == otherIsTest -> SAME
            thisIsTest -> IS_MORE_SPECIFIC
            else -> IS_LESS_SPECIFIC
        }
    }
}