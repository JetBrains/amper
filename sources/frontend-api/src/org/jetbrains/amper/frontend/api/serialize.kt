/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

@RequiresOptIn(
    "This API is introduced before the big frontend refactoring and will likely be changed/replaced by something else.",
    level = RequiresOptIn.Level.WARNING,
)
annotation class UnstableSchemaApi

/**
 * Provides stable string representation of the whole data tree.
 * The format is human-readable but not strictly defined.
 */
@UnstableSchemaApi
fun SchemaNode.toStringRepresentation(): String = valueHolders
    .entries
    .sortedBy { (k, _) -> k }
    .joinToString(
        prefix = "{",
        postfix = "}",
        transform = { (k, v) -> "$k: ${serializeToJsonLike(v.value)}" },
    )

@UnstableSchemaApi
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

    is SchemaNode -> any.toStringRepresentation()
    else -> any.toString()
}

