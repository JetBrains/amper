/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.amper

import com.intellij.amper.lang.AmperLiteral
import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperValue
import com.intellij.amper.lang.impl.collectionItems
import com.intellij.amper.lang.impl.propertyList
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaBundle
import org.jetbrains.amper.frontend.reportBundleError

/**
 * convert content of this node, treating its elements as
 * single keyed objects as [YAMLScalar]s, skipping resulting null values.
 */
context(ProblemReporterContext)
fun <T> AmperObject.convertScalarKeyedMap(
    report: Boolean = true,
    convert: AmperValue.(String) -> T?
): Map<String, T> = collectionItems.mapNotNull {
    // TODO Report entries with multiple keys.
    val asMapping = it.asMappingNode() ?: return@mapNotNull null
    val singleKey = asMapping.propertyList.firstOrNull() ?: return@mapNotNull null
    // TODO Report non scalars.
    // Skip non scalar keys.
    val scalarKey = singleKey.name ?: return@mapNotNull null
    // Skip those, that we failed to convert.
    val converted = singleKey.value?.convert(scalarKey) ?: return@mapNotNull null
    scalarKey to converted
}.toMap()

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