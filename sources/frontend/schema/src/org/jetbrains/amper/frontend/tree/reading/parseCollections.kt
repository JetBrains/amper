/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.reading

import org.jetbrains.amper.frontend.api.InternalTraceSetter
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.EmptyContexts
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.NoValue
import org.jetbrains.amper.frontend.tree.Owned
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem

context(contexts: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseList(psi: YAMLSequence, type: SchemaType.ListType): ListValue<*> {
    fun parseListValue(item: YAMLSequenceItem): TreeValue<*>? {
        // (no value case) No point of issuing `NoValue` here because it can't be later overridden in the list context.
        // So it can't be possibly valid - report it and skip it.
        val value = item.value ?: run {
            reportParsing(item, "validation.structure.missing.unmergeable.value")
            return null
        }
        return parseValue(value, type.elementType)
    }

    return ListValue(
        children = psi.items.mapNotNull { item ->
            parseListValue(item)
        },
        trace = psi.asTrace(),
        contexts = contexts,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseMap(psi: YAMLMapping, type: SchemaType.MapType): Owned {
    val children = psi.keyValues.mapNotNull { keyValue: YAMLKeyValue ->
        parseKeyValueForMap(keyValue, type)
    }
    return mapLikeValue(
        children = children,
        origin = psi,
        type = null,
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
internal fun parseMapFromSequence(psi: YAMLSequence, type: SchemaType.MapType): Owned {
    fun parseSingleKeyValue(item: YAMLSequenceItem): MapLikeValue.Property<*>? {
        val value = item.value
        if (value !is YAMLMapping) {
            reportParsing(item, "validation.types.expected.key.value")
            return null
        }
        val singlePair = value.keyValues.singleOrNull() ?: run {
            reportParsing(value, "validation.types.expected.key.value.single")
            return null
        }
        return parseKeyValueForMap(singlePair, type)
    }

    val children = psi.items.mapNotNull { item ->
        parseSingleKeyValue(item)
    }

    return mapLikeValue(
        origin = psi,
        children = children,
        type = null,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
private fun parseKeyValueForMap(
    psi: YAMLKeyValue,
    mapType: SchemaType.MapType,
): MapLikeValue.Property<*>? {
    val key = YAMLScalarOrKey.parseKey(psi)
        ?: return null
    val keyScalar = parseScalar(key, SchemaType.KeyStringType)
        ?: return null
    return MapLikeValue.Property(
        key = keyScalar.value as String,
        kTrace = key.psi.asTrace(),
        value = parseValueFromKeyValue(psi, mapType.valueType, explicitContexts = EmptyContexts),
        pType = null,
    )
}

context(_: Contexts, _: ParsingConfig, _: ProblemReporter)
internal fun parseValueFromKeyValue(
    keyValue: YAMLKeyValue,
    type: SchemaType,
    explicitContexts: Contexts,
): TreeValue<*> { // We do not have a ParsedResult here because we always return some value and report errors within
    val trace = keyValue.asTrace()
    return when (val value = keyValue.value) {
        null -> NoValue(trace)  // Valid NoValue usage - may be overridden later during merging
        else -> {
            @OptIn(InternalTraceSetter::class)
            parseValue(value, type, explicitContexts)
                ?.copyWithTrace(trace) // Replace the trace to also capture the key
                ?: NoValue(trace)  // Return NoValue even in the case of errors to preserve traceability
        }
    }
}