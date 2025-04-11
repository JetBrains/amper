/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.compose.resources

internal fun String.uppercaseFirstChar(): String =
    transformFirstCharIfNeeded(
        shouldTransform = { it.isLowerCase() },
        transform = { it.uppercaseChar() }
    )

private inline fun String.transformFirstCharIfNeeded(
    shouldTransform: (Char) -> Boolean,
    transform: (Char) -> Char
): String {
    if (isNotEmpty()) {
        val firstChar = this[0]
        if (shouldTransform(firstChar)) {
            val sb = java.lang.StringBuilder(length)
            sb.append(transform(firstChar))
            sb.append(this, 1, length)
            return sb.toString()
        }
    }
    return this
}

internal fun String.asUnderscoredIdentifier(): String =
    replace('-', '_')
        .let { if (it.isNotEmpty() && it.first().isDigit()) "_$it" else it }
