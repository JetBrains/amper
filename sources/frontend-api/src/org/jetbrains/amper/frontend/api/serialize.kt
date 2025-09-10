/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api


/**
 * Provides stable string representation of the whole data tree.
 * The format is JSON-like, stable, human-readable but not strictly defined.
 */
fun SchemaNode.toStableJsonLikeString(): String = valueHolders
    .entries
    .sortedBy { (k, _) -> k }
    .joinToString(
        prefix = "{",
        postfix = "}",
        transform = { (k, v) -> "$k: ${serializeToJsonLike(v.value)}" },
    )

private fun serializeToJsonLike(any: Any?): String = when (any) {
    is Collection<*> -> any.joinToString(
        prefix = "[",
        postfix = "]",
        transform = ::serializeToJsonLike,
    )

    is Map<*, *> -> any.entries.joinToString(
        prefix = "{",
        postfix = "}",
        transform = { (k, v) -> "$k: ${serializeToJsonLike(v)}" }
    )

    is SchemaNode -> any.toStableJsonLikeString()
    else -> any.toString()
}

