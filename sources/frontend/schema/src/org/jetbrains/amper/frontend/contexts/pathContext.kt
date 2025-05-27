/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.INDETERMINATE
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_LESS_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_MORE_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.SAME
import java.nio.file.Path
import kotlin.io.path.absolute


class PathCtx(path: Path, override val trace: Trace? = null) : Context {
    val path: Path = path.absolute().normalize()
    override fun withoutTrace() = PathCtx(path)
    override fun toString() = path.toString()
}

/**
 * Interpret contexts as paths and make inheritance conclusion based on [order] order.
 */
class PathsInheritance(
    val order: List<Path>,
) : ContextsInheritance {
    private fun List<Path>.normalized() = map(Path::absolute).map(Path::normalize)
    private val orderNormalized = order.normalized()
    private fun Contexts.paths() = filterIsInstance<PathCtx>().map { it.path }.normalized()
    private fun Contexts.pathIndices() = paths().map { orderNormalized.indexOf(it) }.filterNot { it == -1 }.sorted()

    override fun Contexts.isMoreSpecificThan(other: Contexts): ContextsInheritance.Result {
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