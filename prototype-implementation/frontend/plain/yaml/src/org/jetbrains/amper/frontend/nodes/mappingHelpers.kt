/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.nodes

fun YamlNode.Mapping.getMapping(key: String): Pair<YamlNode, YamlNode>? =
    mappings.firstOrNull { (k, _) -> (k as? YamlNode.Scalar)?.value == key }

operator fun YamlNode.Mapping.get(key: String): YamlNode? =
    mappings.firstNotNullOfOrNull { (k, v) -> if (k is YamlNode.Scalar && k.value == key) v else null }

val YamlNode.Mapping.keys: Set<String>
    get() = mappings.mapNotNull { (k, _) -> (k as? YamlNode.Scalar)?.value }.toSet()

fun YamlNode.Mapping.getStringValue(key: String): String? = this[key]?.let { (it as? YamlNode.Scalar)?.value }

fun YamlNode.Mapping.getStringValue(key: String, block: (String) -> Unit) = this[key]?.let { (it as? YamlNode.Scalar)?.value }?.let(block)

fun YamlNode.Mapping.getBooleanValue(key: String): Boolean? = getStringValue(key)?.toBooleanStrictOrNull()

fun YamlNode.Mapping.getBooleanValue(key: String, block: (Boolean) -> Unit) = getStringValue(key)?.toBooleanStrictOrNull()?.let(block)

fun YamlNode.Mapping.getSequenceValue(key: String): YamlNode.Sequence? = this[key] as? YamlNode.Sequence

fun YamlNode.Mapping.getSequenceValue(key: String, block: (YamlNode.Sequence) -> Unit) = (this[key] as? YamlNode.Sequence)?.let(block)

fun YamlNode.Mapping.getMappingValue(key: String): YamlNode.Mapping? = this[key] as? YamlNode.Mapping
