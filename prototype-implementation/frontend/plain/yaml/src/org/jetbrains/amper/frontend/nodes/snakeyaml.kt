package org.jetbrains.amper.frontend.nodes

import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import java.nio.file.Path

fun Node.toYamlNode(originalFile: Path? = null): YamlNode? =
    when (this) {
        is ScalarNode -> YamlNode.Scalar(
            value,
            Mark(startMark.line, startMark.column),
            Mark(endMark.line, endMark.column),
            originalFile,
        ).takeIf { it.startMark != it.endMark }

        is SequenceNode -> YamlNode.Sequence(
            value.mapNotNull { it.toYamlNode(originalFile) },
            Mark(startMark.line, startMark.column),
            Mark(endMark.line, endMark.column),
            originalFile,
        )

        is MappingNode -> YamlNode.Mapping(
            value.mapNotNull {
                val keyNode = it.keyNode.toYamlNode(originalFile) ?: return@mapNotNull null
                val valueNode = it.valueNode.toYamlNode(originalFile) ?: return@mapNotNull null
                keyNode to valueNode
            },
            Mark(startMark.line, startMark.column),
            Mark(endMark.line, endMark.column),
            originalFile,
        )

        else -> null
    }
