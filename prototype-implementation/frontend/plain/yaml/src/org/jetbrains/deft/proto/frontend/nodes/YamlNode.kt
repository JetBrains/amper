package org.jetbrains.deft.proto.frontend.nodes

import org.jetbrains.deft.proto.frontend.Settings

data class Mark(val line: Int, val column: Int) {
    companion object {
        val Unknown = Mark(0, 0)
    }
}

sealed interface YamlNode {
    val startMark: Mark
    val endMark: Mark

    data class Scalar(val value: String, override val startMark: Mark, override val endMark: Mark) : YamlNode,
        CharSequence by value

    data class Sequence(val elements: List<YamlNode>, override val startMark: Mark, override val endMark: Mark) :
        YamlNode, List<YamlNode> by elements

    data class Mapping(
        val mappings: List<Pair<YamlNode, YamlNode>>,
        override val startMark: Mark,
        override val endMark: Mark
    ) : YamlNode, List<Pair<YamlNode, YamlNode>> by mappings {
        companion object {
            val Empty = Mapping(emptyList(), Mark.Unknown, Mark.Unknown)
        }
    }
}

val YamlNode.nodeType: String
    get() = when (this) {
        is YamlNode.Scalar -> "string"
        is YamlNode.Sequence -> "list"
        is YamlNode.Mapping -> "map"
    }

operator fun YamlNode.Mapping.get(key: String): YamlNode? =
    mappings.firstNotNullOfOrNull { (k, v) -> if (k is YamlNode.Scalar && k.value == key) v else null }

fun YamlNode.Mapping.toSettings(): Settings = mappings.mapNotNull { (k, v) ->
    if (k is YamlNode.Scalar) k.value to v.convert() else null
}.toMap()

private fun YamlNode.convert(): Any = when (this) {
    is YamlNode.Scalar -> value
    is YamlNode.Sequence -> elements.map { it.convert() }
    is YamlNode.Mapping -> mappings.associate { (k, v) -> k.convert() to v.convert() }
}
