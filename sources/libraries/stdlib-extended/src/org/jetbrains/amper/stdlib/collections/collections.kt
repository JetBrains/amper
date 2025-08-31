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
