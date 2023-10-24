package org.jetbrains.amper.frontend.util

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

/**
 * Gets cartesian product of all elements.
 */
fun <I, T, CT : Collection<T>> Iterable<I>.cartesianGeneric(
    init: () -> CT,
    unfoldIn: I.() -> CT,
    merge: CT.(T) -> CT,
    /**
     * Preserve resulting lower dimension lists. For example, for cartesian "[a, b], [c, d]"
     * elements like "a", "b" will be preserved.
     */
    preserveLowerDimensions: Boolean,
    /**
     * Preserve empty elements.
     */
    preserveEmpty: Boolean,
): List<CT> =
    fold(listOf(init())) { acc, element ->
        val inner = acc.flatMap { current -> element.unfoldIn().map { current.merge(it) } }
        if (preserveLowerDimensions) acc + inner else inner
    }.filter { preserveEmpty || it.isNotEmpty() }

/**
 * Get an input stream from a file, if it exists, or null.
 */
fun Path.inputStreamOrNull() = takeIf { it.exists() }?.inputStream()

/**
 * Require sequence to have only one element or no at all.
 */
fun <T> Sequence<T>.requireSingleOrNull(): T? = iterator().run {
    takeIf { hasNext() }?.next()
        .apply { if (this@run.hasNext()) error("Must be exactly one element or none!") }
}