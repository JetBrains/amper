package org.jetbrains.deft.proto.frontend.util

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