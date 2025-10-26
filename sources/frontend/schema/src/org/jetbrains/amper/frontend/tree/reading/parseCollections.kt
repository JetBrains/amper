/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.tree.ErrorValue
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter

context(contexts: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseList(value: YamlValue.Sequence, type: SchemaType.ListType): ListValue<*> {
    return ListValue(
        children = value.items.mapNotNull { value ->
            parseValue(value, type.elementType)
        },
        type = type,
        trace = value.asTrace(),
        contexts = contexts,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseMap(value: YamlValue.Mapping, type: SchemaType.MapType): Owned {
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
internal fun parseMapFromSequence(value: YamlValue.Sequence, type: SchemaType.MapType): Owned {
    fun parseSingleKeyValue(value: YamlValue): MapLikeValue.Property<*>? {
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
): MapLikeValue.Property<*>? {
    val keyScalar = parseScalarKey(keyValue.key, SchemaType.StringType)
        ?: return null
    return MapLikeValue.Property(
        key = keyScalar.value as String,
        kTrace = keyValue.key.asTrace(),
        value = parseValueFromKeyValue(keyValue, mapType.valueType, explicitContexts = EmptyContexts),
        pType = null,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseScalarKey(
    key: YamlValue,
    type: SchemaType.ScalarType,
): ScalarValue<*>? {
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
internal fun parseValueFromKeyValue(
    keyValue: YamlKeyValue,
    type: SchemaType,
    explicitContexts: Contexts,
): TreeValue<*> {
    val trace = keyValue.asTrace()
    return parseValue(keyValue.value, type, explicitContexts)
        ?.copyWithTrace(trace) // Replace the trace to also capture the key
        ?: ErrorValue(trace)
}