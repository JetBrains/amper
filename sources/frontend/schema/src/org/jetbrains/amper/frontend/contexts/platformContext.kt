/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import com.intellij.util.asSafely
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.INDETERMINATE
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_LESS_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_MORE_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.SAME
import org.jetbrains.amper.frontend.leaves


class PlatformCtx(val value: String, override val trace: Trace? = null) : Context {
    override fun withoutTrace() = PlatformCtx(value)
    override fun toString() = value
}

fun Contexts.platformCtxs() = filterIsInstance<PlatformCtx>().toSet()

/**
 * Interpret contexts as platforms and make
 * inheritance conclusion based on platform hierarchy.
 */
class PlatformsInheritance(
    val aliases: Map<String, Set<Platform>> = emptyMap()
) : ContextsInheritance {
    private val PlatformCtx.leaves get() = Platform[value]?.leaves ?: aliases[value]?.leaves
    private val Contexts.ctxLeaves get() = mapNotNull { it.asSafely<PlatformCtx>()?.leaves }.flatten().toSet()

    override fun Contexts.isMoreSpecificThan(other: Contexts): ContextsInheritance.Result {
        val thisLeaves = ctxLeaves
        val otherLeaves = other.ctxLeaves
        return when {
            thisLeaves == otherLeaves -> SAME
            // Absence of platforms means that it is "star" or "common" context.
            thisLeaves.isEmpty() && !otherLeaves.isEmpty() -> IS_LESS_SPECIFIC
            otherLeaves.isEmpty() && !thisLeaves.isEmpty() -> IS_MORE_SPECIFIC
            // Otherwise just compare platforms.
            otherLeaves.containsAll(thisLeaves) -> IS_MORE_SPECIFIC
            thisLeaves.containsAll(otherLeaves) -> IS_LESS_SPECIFIC
            else -> INDETERMINATE
        }
    }
}