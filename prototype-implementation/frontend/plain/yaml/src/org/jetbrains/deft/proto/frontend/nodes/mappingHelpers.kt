package org.jetbrains.deft.proto.frontend.nodes

import org.jetbrains.deft.proto.frontend.Settings

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
