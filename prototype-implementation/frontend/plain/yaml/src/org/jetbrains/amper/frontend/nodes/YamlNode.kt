/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.nodes

import java.nio.file.Path

data class Mark(val line: Int, val column: Int) {
    companion object {
        val Unknown = Mark(0, 0)
    }
}

sealed interface YamlNode {
    val startMark: Mark
    val endMark: Mark
    val originalFile: Path?

    /**
     * This mark is used to show where this node was referenced from
     * to provide proper backtrace when reporting errors in templates.
     */
    val referencedAt: Mark?

    data class Scalar(
        val value: String,
        override val startMark: Mark,
        override val endMark: Mark,
        override val originalFile: Path? = null,
        override val referencedAt: Mark? = null,
    ) : YamlNode, CharSequence by value

    data class Sequence(
        val elements: List<YamlNode>,
        override val startMark: Mark,
        override val endMark: Mark,
        override val originalFile: Path? = null,
        override val referencedAt: Mark? = null,
    ) : YamlNode, List<YamlNode> by elements {
        companion object {
            val Empty = Sequence(emptyList(), Mark.Unknown, Mark.Unknown)
        }
    }

    data class Mapping(
        val mappings: List<Pair<YamlNode, YamlNode>>,
        override val startMark: Mark,
        override val endMark: Mark,
        override val originalFile: Path? = null,
        override val referencedAt: Mark? = null,
    ) : YamlNode, List<Pair<YamlNode, YamlNode>> by mappings {
        companion object {
            val Empty = Mapping(emptyList(), Mark.Unknown, Mark.Unknown)
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun <YamlNodeT : YamlNode> YamlNodeT.withReference(mark: Mark): YamlNodeT =
    when (this) {
        is YamlNode.Scalar -> copy(referencedAt = mark)
        is YamlNode.Sequence -> copy(referencedAt = mark, elements = elements.map { it.withReference(mark) })
        is YamlNode.Mapping -> copy(
            referencedAt = mark,
            mappings = mappings.map { (key, value) -> key.withReference(mark) to value.withReference(mark) },
        )

        else -> this // KT-21908
    } as YamlNodeT

val YamlNode.nodeType: String
    get() = when (this) {
        is YamlNode.Scalar -> "string"
        is YamlNode.Sequence -> "list"
        is YamlNode.Mapping -> "map"
    }

val YamlNode.pretty: String
    get() = when (this) {
        is YamlNode.Scalar -> value
        is YamlNode.Sequence -> elements.joinToString(", ", "[", "]") { it.pretty }
        is YamlNode.Mapping -> mappings.joinToString("\n, ", "{", "}") { (key, value) -> "${key.pretty}: ${value.pretty}" }
    }
