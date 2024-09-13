/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.amper

import com.intellij.amper.lang.AmperElement
import com.intellij.amper.lang.AmperLiteral
import com.intellij.amper.lang.AmperObject
import com.intellij.amper.lang.AmperProperty
import com.intellij.amper.lang.AmperValue
import com.intellij.amper.lang.impl.collectionItems
import com.intellij.amper.lang.impl.propertyList
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.applyPsiTrace
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.noModifiers
import org.jetbrains.amper.frontend.schemaConverter.psi.yaml.TraceableMap
import kotlin.reflect.KProperty0


/**
 * Try to set property value, provided by lambda and also set trace based on context.
 */
context(AmperElement, ProblemReporterContext)
fun <T> KProperty0<T>.convertSelf(
    newValue: () -> T?
) {
    val calculated = newValue()
    valueBase?.invoke(calculated)
    valueBase?.applyPsiTrace(this@AmperElement)
    if (calculated is Traceable) calculated.applyPsiTrace(this@AmperElement)
}


/**
 * Try to set property value by searching node child with name
 * same as property name and converting it.
 */
context(AmperObject, ProblemReporterContext)
fun <T> KProperty0<T>.convertChild(
    convertValue: AmperProperty.() -> T?
) {
    findProperty(name)?.let { child ->
        val newValue = convertValue(child)
        valueBase?.invoke(newValue)
        valueBase?.applyPsiTrace(child)
        if (newValue is Traceable) newValue.applyPsiTrace(child)
    }
}

/**
 * Try to set property value by searching node child with name
 * same as property name and converting it.
 */
context(AmperObject, ProblemReporterContext)
fun <T> KProperty0<T>.convertChildValue(
    convertValue: AmperProperty.() -> T?
) {
    findProperty(name)?.let { childValue ->
        val newValue = convertValue(childValue)
        valueBase?.invoke(newValue)
        valueBase?.applyPsiTrace(childValue)
        if (newValue is Traceable) newValue.applyPsiTrace(childValue)
    }
}

/**
 * Try to set property value by searching scalar node child with name
 * same as property name and converting it.
 */
context(AmperObject, ProblemReporterContext)
fun <T> KProperty0<T>.convertChildScalar(
    convertValue: AmperLiteral.() -> T?,
) = convertChild {
    (this.value as? AmperLiteral)?.let(convertValue)
}

/**
 * Try to set string property value by searching scalar node child with name
 * same as property name.
 */
context(AmperObject, ProblemReporterContext)
fun KProperty0<String?>.convertChildString() =
    convertChildScalar { textValue }

/**
 * Try to set int property value by searching scalar node child with name
 * same as property name.
 */
context(AmperObject, ProblemReporterContext)
fun KProperty0<Int?>.convertChildInt() =
    convertChildScalar { textValue.toIntOrNull() }

/**
 * Try to set boolean property value by searching scalar node child with name
 * same as property name.
 */
context(AmperObject, ProblemReporterContext)
fun KProperty0<Boolean?>.convertChildBoolean() =
    convertChildScalar { textValue.toBooleanStrictOrNull() }

/**
 * Try to set enum property value by searching scalar node child with name
 * same as property name.
 */
context(AmperObject, ProblemReporterContext)
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
context(AmperObject, ProblemReporterContext)
fun <T> KProperty0<List<T>?>.convertChildCollection(
    convertValue: AmperValue.() -> T?,
) = convertChild {
    val sequence = value as? AmperObject
    sequence?.collectionItems?.mapNotNull(convertValue)
}

/**
 * Try to set collection property value by searching scalar sequence node child with name
 * same as property name and converting its children.
 */
context(AmperObject, ProblemReporterContext)
fun <T> KProperty0<List<T>?>.convertChildScalarCollection(
    convertValue: AmperLiteral.() -> T?,
) = convertChildCollection {
    (this as? AmperLiteral)?.let(convertValue)
}

/**
 * Try to find all matching child nodes and then map their values.
 */
context(AmperObject, ProblemReporterContext)
fun <T> KProperty0<Map<Modifiers, T>?>.convertModifierAware(
    noModifiersEntry: T? = null,
    convertValue: AmperProperty.() -> T?,
) {
    val newValue = TraceableMap<Modifiers, T>().apply {
        if (noModifiersEntry != null) put(noModifiers, noModifiersEntry)
        propertyList
            .filter { it.name?.startsWith(name) == true }
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