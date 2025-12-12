/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.tree.ErrorNode
import org.jetbrains.amper.frontend.tree.ListNode
import org.jetbrains.amper.frontend.tree.KeyValue
import org.jetbrains.amper.frontend.tree.MappingNode
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

context(contexts: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseList(value: YamlValue.Sequence, type: SchemaType.ListType): ListNode {
    return ListNode(
        children = value.items.mapNotNull { value ->
            parseNode(value, type.elementType)
        },
        type = type,
        trace = value.asTrace(),
        contexts = contexts,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseMap(value: YamlValue.Mapping, type: SchemaType.MapType): MappingNode {
    val children = value.keyValues.mapNotNull { keyValue: YamlKeyValue ->
        parseKeyValueForMap(keyValue, type)
    }
    return mapLikeValue(
        children = children,
        origin = value,
        type = type,
    )
}

/**
 * Or Amper YAML allows to parse Map<String, T> from a sorta List<Pair<String, T>>:
 * ```yaml
 * map:
 *   - key1: value1
 *   - key2: value2
 * ```
 * yields: `{ key1: value1, key2: value2 }`
 */
context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseMapFromSequence(value: YamlValue.Sequence, type: SchemaType.MapType): MappingNode {
    fun parseSingleKeyValue(value: YamlValue): KeyValue? {
        if (value !is YamlValue.Mapping) {
            reportParsing(value, "validation.types.expected.key.value")
            return null
        }
        val singlePair = value.keyValues.singleOrNull() ?: run {
            reportParsing(value, "validation.types.expected.key.value.single")
            return null
        }
        return parseKeyValueForMap(singlePair, type)
    }

    val children = value.items.mapNotNull {
        parseSingleKeyValue(it)
    }

    return mapLikeValue(
        origin = value,
        children = children,
        type = type,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
private fun parseKeyValueForMap(
    keyValue: YamlKeyValue,
    mapType: SchemaType.MapType,
): KeyValue? {
    val keyScalar = parseScalarKey(keyValue.key, SchemaType.StringType)
        ?: return null
    return KeyValue(
        key = keyScalar.value as String,
        keyTrace = keyValue.key.asTrace(),
        trace = keyValue.asTrace(),
        value = parseNodeFromKeyValue(keyValue, mapType.valueType, explicitContexts = EmptyContexts),
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseScalarKey(
    key: YamlValue,
    type: SchemaType.ScalarType,
): ScalarNode? {
    key.tag?.let { tag ->
        if (tag.text.startsWith("!!")) {
            reportParsing(tag, "validation.structure.unsupported.standard.tag", tag.text)
        } else {
            reportParsing(tag, "validation.structure.unsupported.tag")
        }
    }
    when (key) {
        is YamlValue.Missing -> {
            reportParsing(key, "validation.structure.missing.key")
            return null
        }
        is YamlValue.Scalar -> {
            if (containsReferenceSyntax(key)) {
                reportParsing(key, "validation.types.unsupported.reference.key", level = Level.Warning)
            }
            return parseScalar(key, type)
        }
        else -> {
            reportParsing(key, "validation.types.unexpected.compound.key")
            return null
        }
    }
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseNodeFromKeyValue(
    keyValue: YamlKeyValue,
    type: SchemaType,
    explicitContexts: Contexts,
): TreeNode {
    return parseNode(keyValue.value, type, explicitContexts)
        ?: ErrorNode(keyValue.asTrace())
}