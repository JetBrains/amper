/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.util

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.noModifiers
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLValue
import kotlin.reflect.KProperty0


/**
 * Try to set property value, provided by lambda and also set trace based on context.
 */
context(YAMLPsiElement, ProblemReporterContext)
fun <T> KProperty0<T>.convertSelf(
    newValue: () -> T?
) {
    val calculated = newValue()
    valueBase?.invoke(calculated)
    valueBase?.adjustTrace(this@YAMLPsiElement)
    if (calculated is Traceable) calculated.adjustTrace(this@YAMLPsiElement)
}


/**
 * Try to set property value by searching node child with name
 * same as property name and converting it.
 */
context(YAMLMapping, ProblemReporterContext)
fun <T> KProperty0<T>.convertChild(
    convertValue: YAMLKeyValue.() -> T?
) {
    tryGetChildNode(name)?.let { child ->
        val newValue = convertValue(child)
        valueBase?.invoke(newValue)
        valueBase?.adjustTrace(child)
        if (newValue is Traceable) newValue.adjustTrace(child)
    }
}

/**
 * Try to set property value by searching node child with name
 * same as property name and converting it.
 */
context(YAMLMapping, ProblemReporterContext)
fun <T> KProperty0<T>.convertChildValue(
    convertValue: YAMLValue.() -> T?
) {
    tryGetChildNode(name)?.let(YAMLKeyValue::getValue)?.let { childValue ->
        val newValue = convertValue(childValue)
        valueBase?.invoke(newValue)
        valueBase?.adjustTrace(childValue)
        if (newValue is Traceable) newValue.adjustTrace(childValue)
    }
}

/**
 * Try to set property value by searching scalar node child with name
 * same as property name and converting it.
 */
context(YAMLMapping, ProblemReporterContext)
fun <T> KProperty0<T>.convertChildScalar(
    convertValue: YAMLScalar.() -> T?,
) = convertChild {
    asScalarNode()?.let(convertValue)
}

/**
 * Try to set string property value by searching scalar node child with name
 * same as property name.
 */
context(YAMLMapping, ProblemReporterContext)
fun KProperty0<String?>.convertChildString() =
    convertChildScalar { textValue }

/**
 * Try to set boolean property value by searching scalar node child with name
 * same as property name.
 */
context(YAMLMapping, ProblemReporterContext)
fun KProperty0<Boolean?>.convertChildBoolean() =
    convertChildScalar { textValue.toBooleanStrictOrNull() }

/**
 * Try to set enum property value by searching scalar node child with name
 * same as property name.
 */
context(YAMLMapping, ProblemReporterContext)
fun <T : Enum<T>> KProperty0<T?>.convertChildEnum(
    enumIndex: EnumMap<T, String>,
    isFatal: Boolean = false,
    isLong: Boolean = false,
) = convertChildScalar {
    convertEnum(enumIndex, isFatal = isFatal, isLong = isLong)
}

/**
 * Try to set collection property value by searching sequence node child with name
 * same as property name and converting its children.
 */
context(YAMLMapping, ProblemReporterContext)
fun <T> KProperty0<List<T>?>.convertChildCollection(
    convertValue: YAMLValue.() -> T?,
) = convertChild {
    val sequence = value?.asSequenceNode()
    sequence?.items?.mapNotNull { it.value }?.mapNotNull(convertValue)
}

/**
 * Try to set collection property value by searching scalar sequence node child with name
 * same as property name and converting its children.
 */
context(YAMLMapping, ProblemReporterContext)
fun <T> KProperty0<List<T>?>.convertChildScalarCollection(
    convertValue: YAMLScalar.() -> T?,
) = convertChildCollection {
    asScalarNode()?.let(convertValue)
}

/**
 * Try to find all matching child nodes and then map their values.
 */
context(YAMLMapping, ProblemReporterContext)
fun <T> KProperty0<Map<Modifiers, T>?>.convertModifierAware(
    noModifiersEntry: T? = null,
    convertValue: YAMLKeyValue.() -> T?,
) {
    val newValue = buildMap {
        if (noModifiersEntry != null) put(noModifiers, noModifiersEntry)
        keyValues
            .filter { it.keyText.startsWith(name) }
            .forEach {
                val modifiers = it.extractModifiers()
                // Skip those, that we failed to convert.
                convertValue(it)?.let {
                    newValue -> put(modifiers, newValue)
                }
            }
    }


    valueBase?.invoke(newValue)
}