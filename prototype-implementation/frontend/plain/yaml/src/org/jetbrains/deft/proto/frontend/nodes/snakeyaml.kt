package org.jetbrains.deft.proto.frontend.nodes

import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode

fun Node.toYamlNode(): YamlNode? =
    when (this) {
        is ScalarNode -> YamlNode.Scalar(
            value,
            Mark(startMark.line, startMark.column),
            Mark(endMark.line, endMark.column)
        ).takeIf { it.startMark != it.endMark }

        is SequenceNode -> YamlNode.Sequence(
            value.mapNotNull { it.toYamlNode() },
            Mark(startMark.line, startMark.column),
            Mark(endMark.line, endMark.column)
        )

        is MappingNode -> YamlNode.Mapping(value.mapNotNull {
            val keyNode = it.keyNode.toYamlNode() ?: return@mapNotNull null
            val valueNode = it.valueNode.toYamlNode() ?: return@mapNotNull null
            keyNode to valueNode
        }, Mark(startMark.line, startMark.column), Mark(endMark.line, endMark.column))

        else -> null
    }
