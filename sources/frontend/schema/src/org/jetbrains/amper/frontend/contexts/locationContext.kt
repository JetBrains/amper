/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.INDETERMINATE
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_LESS_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_MORE_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.SAME


class PathCtx(val path: VirtualFile, override val trace: Trace? = null) : Context {
    override fun withoutTrace() = PathCtx(path)
    override fun toString() = path.path
}

/**
 * Interpret contexts as paths and make inheritance conclusion based on [order] order.
 */
class PathInheritance(
    val order: List<VirtualFile>,
) : ContextsInheritance<PathCtx> {
    private val orderAsStrings = order.map { it.path }
    private fun Collection<PathCtx>.pathIndices() = 
        map { orderAsStrings.indexOf(it.path.path) }.filterNot { it == -1 }.sorted()

    override fun Collection<PathCtx>.isMoreSpecificThan(other: Collection<PathCtx>): ContextsInheritance.Result {
        val thisIndices = pathIndices()
        val otherIndices = other.pathIndices()

        return when {
            // Paths are the same
            thisIndices == otherIndices -> SAME
            // We treat absence of path ctx as the most generic ctx.
            thisIndices.isEmpty() -> IS_LESS_SPECIFIC
            otherIndices.isEmpty() -> IS_MORE_SPECIFIC
            // All paths from this precede all paths from the other.
            thisIndices.min() > otherIndices.max() -> IS_MORE_SPECIFIC
            // All paths from the other precede all paths from this.
            thisIndices.max() < otherIndices.min() -> IS_LESS_SPECIFIC
            // Paths are mixed, no order is guaranteed.
            else -> INDETERMINATE
        }
    }
}