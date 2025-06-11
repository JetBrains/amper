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
 * Treat non-tests these as less specific.
 */
object MainTestInheritance : ContextsInheritance<TestCtx> {
    override fun Collection<TestCtx>.isMoreSpecificThan(other: Collection<TestCtx>): ContextsInheritance.Result {
        val thisIsTest = this.isNotEmpty()
        val otherIsTest = other.isNotEmpty()
        return when {
            thisIsTest == otherIsTest -> SAME
            thisIsTest -> IS_MORE_SPECIFIC
            else -> IS_LESS_SPECIFIC
        }
    }
}