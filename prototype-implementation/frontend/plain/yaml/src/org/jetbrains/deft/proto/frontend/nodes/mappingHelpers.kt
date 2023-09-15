package org.jetbrains.deft.proto.frontend.nodes

import org.jetbrains.deft.proto.frontend.Settings

fun YamlNode.Mapping.getMapping(key: String): Pair<YamlNode, YamlNode>? =
    mappings.firstOrNull { (k, _) -> (k as? YamlNode.Scalar)?.value == key }

operator fun YamlNode.Mapping.get(key: String): YamlNode? =
    mappings.firstNotNullOfOrNull { (k, v) -> if (k is YamlNode.Scalar && k.value == key) v else null }

val YamlNode.Mapping.keys: Set<String>
    get() = mappings.mapNotNull { (k, _) -> (k as? YamlNode.Scalar)?.value }.toSet()

fun YamlNode.Mapping.toSettings(): Settings = mappings.mapNotNull { (k, v) ->
    if (k is YamlNode.Scalar) k.value to v.convert() else null
}.toMap()

private fun YamlNode.convert(): Any = when (this) {
    is YamlNode.Scalar -> value
    is YamlNode.Sequence -> elements.map { it.convert() }
    is YamlNode.Mapping -> mappings.associate { (k, v) -> k.convert() to v.convert() }
}
