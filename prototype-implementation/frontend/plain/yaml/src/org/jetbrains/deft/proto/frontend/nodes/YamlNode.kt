package org.jetbrains.deft.proto.frontend.nodes

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
        YamlNode, List<YamlNode> by elements {
            companion object {
                val Empty = Sequence(emptyList(), Mark.Unknown, Mark.Unknown)
            }
        }

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
