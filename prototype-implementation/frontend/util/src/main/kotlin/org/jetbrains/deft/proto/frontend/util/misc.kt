package org.jetbrains.deft.proto.frontend.util

/**
 * Gets cartesian product of all elements.
 */
fun <I, T, CT : Collection<T>> Iterable<I>.cartesianGeneric(
    init: () -> CT,
    unfoldIn: I.() -> CT,
    merge: CT.(T) -> CT,
    preserveEmpty: Boolean = false,
): List<CT> =
    fold(listOf(init())) { acc, element ->
        acc + acc.flatMap { current -> element.unfoldIn().map { current.merge(it) } }
    }.filter { preserveEmpty || it.isNotEmpty() }