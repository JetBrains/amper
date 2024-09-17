/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.applyPsiTrace
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schemaConverter.psi.ConvertCtx
import org.jetbrains.amper.frontend.schemaConverter.psi.filterNotNullValues


/**
 * convert the content of this node, treating its elements as
 *   single-keyed objects as scalars, skipping resulting null values.
 */
context(ProblemReporterContext)
fun <T> Sequence.convertScalarKeyedMap(
    convert: PsiElement.(String) -> T?
): Map<String, T> = items.mapNotNull {
    // TODO Report entries with multiple keys.
    val asMapping = it.asMappingNode() ?: return@mapNotNull null
    val singleKey = asMapping.keyValues.firstOrNull() ?: return@mapNotNull null
    // TODO Report non scalars.
    // Skip non scalar keys.
    val scalarKey = singleKey.keyText ?: return@mapNotNull null
    // Skip those that we failed to convert.
    val converted = singleKey.value?.convert(scalarKey) ?: return@mapNotNull null
    scalarKey to converted
}.toMap()

/**
 * Converts this [MappingNode] into a map of [TraceableString] keys and values, with proper links to [PsiElement]s.
 */
context(ProblemReporterContext, ConvertCtx)
internal fun MappingNode.convertTraceableStringMap(): Map<TraceableString, TraceableString> = convertMap(
    convertKey = { TraceableString(it) },
    convertValue = {
        TraceableString(it.asScalarNode()!!.textValue)
    },
).filterNotNullValues()

/**
 * Converts this [MappingNode] into a map using the given conversion functions [convertKey] and [convertValue].
 *
 * If the values returned by the conversion functions are [Traceable], they are automatically associated with the
 * corresponding [PsiElement].
 *
 * Invalid elements inside this object are ignored, and the conversion functions are not called for them.
 */
context(ProblemReporterContext, ConvertCtx)
internal fun <K, V> MappingNode.convertMap(
    convertKey: (String) -> K,
    convertValue: (PsiElement) -> V,
): Map<K, V> = keyValues.mapNotNull { it.convertPair(convertKey, convertValue) }.toMap()

context(ProblemReporterContext, ConvertCtx)
private fun <K, V> MappingEntry.convertPair(
    convertKey: (String) -> K,
    convertValue: (PsiElement) -> V,
): Pair<K, V>? {
    val key = keyText ?: return null
    val value = value ?: return null

    val convertedKey = convertKey(key)
    if (convertedKey is Traceable) {
        convertedKey.applyPsiTrace(keyElement)
    }
    val convertedValue = convertValue(value)
    if (convertedValue is Traceable) {
        convertedValue.applyPsiTrace(value)
    }
    return convertedKey to convertedValue
}

/**
 * Convert this scalar node as an enum, reporting non-existent values.
 */
context(ProblemReporterContext)
fun <T : Enum<T>, V : PsiElement?> V.convertEnum(
    enumIndex: EnumMap<T, String>,
    isFatal: Boolean = false,
    isLong: Boolean = false
): T? = this?.textValue?.let {
    val receivedValue = enumIndex[it]
    if (receivedValue == null) {
        if (isLong) {
            SchemaBundle.reportBundleError(
                node = this,
                messageKey = "unknown.property.type.long",
                enumIndex.enumClass.simpleName!!,
                it,
                enumIndex.keys,
                level = if (isFatal) Level.Fatal else Level.Error
            )
        } else {
            SchemaBundle.reportBundleError(
                node = this,
                messageKey = "unknown.property.type",
                enumIndex.enumClass.simpleName!!,
                it,
                level = if (isFatal) Level.Fatal else Level.Error
            )
        }

    }
    receivedValue
}