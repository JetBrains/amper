/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.util

import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLValue


/**
 * convert content of this node, treating its keys as [YAMLScalar]s,
 * skipping resulting null values.
 */
context(ProblemReporterContext)
fun <T> YAMLMapping.convertScalarKeyedMap(
    report: Boolean = true,
    convert: YAMLValue.(String) -> T?
): Map<String, T> = keyValues.mapNotNull {
    // TODO Report non scalars.
    // Skip non scalar keys.
    val scalarKey = it.keyText
    // Skip those, that we failed to convert.
    val converted = it.value?.convert(scalarKey) ?: return@mapNotNull null
    scalarKey to converted
}.toMap()

/**
 * Convert this scalar node as enum, reporting non-existent values.
 */
context(ProblemReporterContext)
fun <T : Enum<T>, V : YAMLScalar?> V.convertEnum(
    enumIndex: EnumMap<T, String>,
    isFatal: Boolean = false,
    isLong: Boolean = false
): T? = this?.textValue?.let {
    val receivedValue = enumIndex[it]
    if (receivedValue == null) {
        if (isLong) {
            SchemaBundle.reportBundleError(
                this,
                "unknown.property.type.long",
                enumIndex.enumClass.simpleName!!,
                it,
                enumIndex.keys,
                level = if (isFatal) Level.Fatal else Level.Error
            )
        } else {
            SchemaBundle.reportBundleError(
                node = this,
                "unknown.property.type",
                enumIndex.enumClass.simpleName!!,
                it,
                level = if (isFatal) Level.Fatal else Level.Error
            )
        }

    }
    receivedValue
}