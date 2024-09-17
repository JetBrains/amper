/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi.yaml

import com.intellij.psi.PsiElement
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.applyPsiTrace
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.noModifiers
import kotlin.reflect.KProperty0


/**
 * Try to set property value, provided by lambda and also set trace based on context.
 */
context(PsiElement, ProblemReporterContext)
fun <T> KProperty0<T>.convertSelf(
    newValue: () -> T?
) {
    val calculated = newValue()
    valueBase?.invoke(calculated)
    valueBase?.applyPsiTrace(this@PsiElement)
    if (calculated is Traceable) calculated.applyPsiTrace(this@PsiElement)
}

/**
 * Try to set property value by searching node child with name
 * same as property name and converting it.
 */
context(MappingNode, ProblemReporterContext)
fun <T> KProperty0<T>.convertChild(
    convertValue: MappingEntry.() -> T?
) {
    tryGetChildNode(name)?.let { child ->
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
context(MappingNode, ProblemReporterContext)
fun <T> KProperty0<T>.convertChildValue(
    convertValue: MappingEntry.() -> T?
) {
    tryGetChildNode(name)?.let { childValue ->
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
context(MappingNode, ProblemReporterContext)
fun <T> KProperty0<T>.convertChildScalar(
    convertValue: PsiElement.() -> T?,
) = convertChild {
    sourceElement.asScalarNode()?.let(convertValue)
}

/**
 * Try to set string property value by searching scalar node child with name
 * same as property name.
 */
context(MappingNode, ProblemReporterContext)
fun KProperty0<String?>.convertChildString() =
    convertChildScalar { textValue }

/**
 * Try to set int property value by searching scalar node child with name
 * same as property name.
 */
context(YAMLMapping, ProblemReporterContext)
fun KProperty0<Int?>.convertChildInt() =
    convertChildScalar { textValue.toIntOrNull() }

/**
 * Try to set boolean property value by searching scalar node child with name
 * same as property name.
 */
context(MappingNode, ProblemReporterContext)
fun KProperty0<Boolean?>.convertChildBoolean() =
    convertChildScalar { textValue.toBooleanStrictOrNull() }

/**
 * Try to set enum property value by searching scalar node child with name
 * same as property name.
 */
context(MappingNode, ProblemReporterContext)
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
context(MappingNode, ProblemReporterContext)
fun <T> KProperty0<List<T>?>.convertChildCollection(
    convertValue: PsiElement.() -> T?,
) = convertChild {
    val sequence = value?.asSequenceNode()
    sequence?.items?.mapNotNull(convertValue)
}

/**
 * Try to set collection property value by searching scalar sequence node child with name
 * same as property name and converting its children.
 */
context(MappingNode, ProblemReporterContext)
fun <T> KProperty0<List<T>?>.convertChildScalarCollection(
    convertValue: PsiElement.() -> T?,
) = convertChildCollection {
    asScalarNode()?.let(convertValue)
}

/**
 * Try to find all matching child nodes and then map their values.
 */
context(MappingNode, ProblemReporterContext)
fun <T> KProperty0<Map<Modifiers, T>?>.convertModifierAware(
    noModifiersEntry: T? = null,
    convertValue: MappingEntry.() -> T?,
) {
    val newValue = TraceableMap<Modifiers, T>()
    if (noModifiersEntry != null) newValue.put(noModifiers, noModifiersEntry)
    keyValues
        .filter { it.keyText.orEmpty().startsWith(name) }
        .forEach {
            val modifiers = it.extractModifiers()
            // Skip those that we failed to convert.
            convertValue(it)?.let { v ->
                newValue.put(modifiers, v)
            }
        }
    valueBase?.invoke(newValue)
}

class TraceableMap<K, V> : HashMap<K, V>(), Traceable {
    private val traces = mutableListOf<Trace>()

    override var trace: Trace?
        get() = traces.filterIsInstance<PsiTrace>().distinctBy { it.psiElement }.singleOrNull()
        set(value) { if (value is PsiTrace) { traces.clear(); traces.add(value) } }

    override fun put(key: K, value: V): V? {
        if (value is Traceable) value.trace?.let { traces.add(it) }
        return super.put(key, value)
    }
}