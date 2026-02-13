/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.CompleteListNode
import org.jetbrains.amper.frontend.tree.CompleteMapNode
import org.jetbrains.amper.frontend.tree.CompleteObjectNode
import org.jetbrains.amper.frontend.tree.CompleteTreeNode
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.IntNode
import org.jetbrains.amper.frontend.tree.NullLiteralNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.StringNode
import kotlin.io.path.pathString

/**
 * Provides stable string representation of the whole data tree.
 * The format is JSON-like, stable, human-readable but not strictly defined.
 */
fun SchemaNode.toStableJsonLikeString(): String = backingTree.toStableJsonLikeString()

fun CompleteObjectNode.toStableJsonLikeString(): String = refinedChildren
    .entries
    .sortedBy { (k, _) -> k }
    .joinToString(
        prefix = "{",
        postfix = "}",
        transform = { (k, kv) -> "$k: ${serializeToJsonLike(kv.value)}" },
    )

private fun serializeToJsonLike(node: CompleteTreeNode): String = when (node) {
    is CompleteListNode -> node.children.joinToString(
        prefix = "[",
        postfix = "]",
        transform = ::serializeToJsonLike,
    )

    is CompleteMapNode -> node.refinedChildren.entries.joinToString(
        prefix = "{",
        postfix = "}",
        transform = { (k, kv) -> "$k: ${serializeToJsonLike(kv.value)}" }
    )

    is CompleteObjectNode -> node.toStableJsonLikeString()
    is NullLiteralNode -> "null"
    is BooleanNode -> node.value.toString()
    is EnumNode -> node.entryName
    is IntNode -> node.value.toString()
    is PathNode -> node.value.pathString
    is StringNode -> node.value
}

