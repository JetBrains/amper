/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.stdlib.collections

/**
 * Returns a string containing all the elements separated using the given separators.
 *
 * * For empty lists or lists of size 1, separators are irrelevant and ignored.
 * * For lists of size 2, the [separatorForSize2] is used.
 * * For bigger lists, [separator] is used between all elements except the last pair which uses [lastSeparator].
 *
 * Dynamically choosing separators allows to generate strings like `"1, 2, 3, and 4"` (with Oxford comma),
 * or `"1 and 2"` (without Oxford comma) using the same function call.
 */
fun <T> List<T>.joinToString(
    separator: CharSequence = ", ",
    lastSeparator: CharSequence = separator,
    separatorForSize2: CharSequence = separator,
    transform: ((T) -> CharSequence)? = null,
): String {
    val list = this // to avoid mistakes with StringBuilder's functions/properties inside the builder block
    return buildString {
        for (i in list.indices) {
            if (i > 0) {
                val effectiveSeparator = when {
                    list.size == 2 -> separatorForSize2
                    i == lastIndex -> lastSeparator
                    else -> separator
                }
                append(effectiveSeparator)
            }
            appendElement(list[i], transform)
        }
    }
}

private fun <T> Appendable.appendElement(element: T, transform: ((T) -> CharSequence)?) {
    when {
        transform != null -> append(transform(element))
        element is CharSequence? -> append(element)
        element is Char -> append(element)
        else -> append(element.toString())
    }
}

/**
 * Returns a list containing only elements from the given iterable
 * having distinct keys returned by the given [selector] function.
 *
 * Among elements of the given iterable with equal keys, only the first one will be present in the resulting list.
 * The elements in the resulting list are in the same order as they were in the source iterable.
 *
 * [onDuplicates] is invoked for each unique key, that has two or more corresponding elements.
 */
fun <T, K> Iterable<T>.distinctBy(
    selector: (T) -> K,
    onDuplicates: (key: K, items: List<T>) -> Unit,
): List<T> {
    return groupBy(selector)
        .onEach { (key, items) ->
            if (items.size > 1) {
                onDuplicates(key, items)
            }
        }.values
        .map { it.first() }
}

/**
 * Returns a list of pairs of each two adjacent elements in this collection.
 * The last pair in the resulting list will have `null` for its second element.
 *
 * The returned list is empty if this iterable has no elements.
 */
fun <T> Iterable<T>.zipWithNextOrNull(): List<Pair<T, T?>> {
    val iterator = iterator()
    if (!iterator.hasNext()) return emptyList()
    return buildList {
        var current = iterator.next()
        while (true) {
            if (!iterator.hasNext()) {
                add(current to null)
                break
            }
            val next = iterator.next()
            add(current to next)
            current = next
        }
    }
}

/**
 * Returns a [Map] containing the values provided by [valueTransform]\
 * and indexed by [keySelector] functions applied to elements of the given collection.
 * If [valueTransform] returns `null` for any element, that element is ignored.
 *
 * If any two elements would have the same key returned by [keySelector] the last one gets added to the map.
 *
 * The returned map preserves the entry iteration order of the original collection.
 */
inline fun <T, K, V : Any> Iterable<T>.associateByNotNull(keySelector: (T) -> K, valueTransform: (T) -> V?): Map<K, V> {
    return buildMap {
        for (element in this@associateByNotNull) {
            val key = keySelector(element)
            val value = valueTransform(element)
            if (key != null && value != null) {
                put(key, value)
            }
        }
    }
}
