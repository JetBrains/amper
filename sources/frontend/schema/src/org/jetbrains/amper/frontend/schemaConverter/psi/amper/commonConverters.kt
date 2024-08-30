/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.amper

import com.intellij.amper.lang.AmperLiteral
import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperProperty
import com.intellij.amper.lang.AmperValue
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
import org.jetbrains.amper.frontend.schemaConverter.psi.assertNodeType
import org.jetbrains.amper.frontend.schemaConverter.psi.filterNotNullValues

/**
 * Converts this [AmperObject] into a string-keyed map using the given conversion function [convertValue].
 *
 * If the values returned by the conversion function are [Traceable], they are automatically associated with the
 * corresponding [PsiElement].
 *
 * Invalid elements inside this object are ignored, and the conversion function is not called for them.
 */
context(ProblemReporterContext, ConvertCtx)
internal fun <V> AmperObject.convertMap(convertValue: (AmperValue) -> V): Map<String, V> = convertMap(
    convertKey = { it },
    convertValue = { convertValue(it) },
)

/**
 * Converts this [AmperObject] into a map of [TraceableString] keys and values, with proper links to [PsiElement]s.
 */
context(ProblemReporterContext, ConvertCtx)
internal fun AmperObject.convertTraceableStringMap(): Map<TraceableString, TraceableString> = convertMap(
    convertKey = { TraceableString(it) },
    convertValue = {
        it.assertNodeType<AmperLiteral, TraceableString>(fieldName = "map value") { asTraceableString() }
    },
).filterNotNullValues()

/**
 * Converts this [AmperObject] into a map using the given conversion functions [convertKey] and [convertValue].
 *
 * If the values returned by the conversion functions are [Traceable], they are automatically associated with the
 * corresponding [PsiElement].
 *
 * Invalid elements inside this object are ignored, and the conversion functions are not called for them.
 */
context(ProblemReporterContext, ConvertCtx)
internal fun <K, V> AmperObject.convertMap(
    convertKey: (String) -> K,
    convertValue: (AmperValue) -> V,
): Map<K, V> = objectElementList
    .mapNotNull {
        it.assertNodeType<AmperProperty, Pair<K, V>?>(fieldName = "map entry") {
            convertPair(convertKey, convertValue)
        }
    }.toMap()

context(ProblemReporterContext, ConvertCtx)
private fun <K, V> AmperProperty.convertPair(
    convertKey: (String) -> K,
    convertValue: (AmperValue) -> V,
): Pair<K, V>? {
    val keyElement = nameElement ?: return null
    val key = name ?: return null
    val valueElement = value ?: return null

    val convertedKey = convertKey(key)
    if (convertedKey is Traceable) {
        convertedKey.applyPsiTrace(keyElement)
    }
    val convertedValue = convertValue(valueElement)
    if (convertedValue is Traceable) {
        convertedValue.applyPsiTrace(valueElement)
    }
    return convertedKey to convertedValue
}

/**
 * Convert this scalar node as enum, reporting non-existent values.
 */
context(ProblemReporterContext)
fun <T : Enum<T>, V : AmperLiteral?> V.convertEnum(
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