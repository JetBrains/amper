/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

import com.intellij.psi.PsiElement
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
context(Converter)
fun <T> Scalar.convertSelf(
    property: KProperty0<T>,
    newValue: () -> T?
) {
    val calculated = newValue()
    property.valueBase?.invoke(calculated)
    property.valueBase?.applyPsiTrace(this@Scalar.sourceElement)
    if (calculated is Traceable) calculated.applyPsiTrace(this@Scalar.sourceElement)
}

/**
 * Try to set property value, provided by lambda and also set trace based on context.
 */
context(Converter)
fun <T> MappingEntry.convertEntryItself(
    property: KProperty0<T>,
    newValue: () -> T?
) {
    val calculated = newValue()
    property.valueBase?.invoke(calculated)
    property.valueBase?.applyPsiTrace(sourceElement)
    if (calculated is Traceable) calculated.applyPsiTrace(sourceElement)
}


/**
 * Try to set property value by searching node child with name
 * same as property name and converting it.
 */
context(Converter)
fun <T> MappingNode.convertChild(
    property: KProperty0<T>,
    convertValue: MappingEntry.() -> T?
) {
    tryGetChildNode(property.name)?.let { child ->
        val newValue = convertValue(child)
        property.valueBase?.invoke(newValue)
        property.valueBase?.applyPsiTrace(child)
        if (newValue is Traceable) newValue.applyPsiTrace(child)
    }
}

/**
 * Try to set property value by searching node child with name
 * same as property name and converting it.
 */
context(Converter)
fun <T> MappingNode.convertChildValue(
    property: KProperty0<T>,
    convertValue: PsiElement.() -> T?
) {
    tryGetChildNode(property.name)?.let { childValue ->
        val newValue = childValue.value?.let { convertValue(it) }
        property.valueBase?.invoke(newValue)
        property.valueBase?.applyPsiTrace(childValue)
        if (newValue is Traceable) newValue.applyPsiTrace(childValue)
    }
}

context(Converter)
fun <T> MappingNode.convertChildMapping(
    property: KProperty0<T>,
    convertValue: MappingNode.() -> T?
) {
    tryGetChildNode(property.name)?.let { childValue ->
        val newValue = childValue.value?.let { it.asMappingNode()?.let { v -> convertValue(v) } }
        property.valueBase?.invoke(newValue)
        property.valueBase?.applyPsiTrace(childValue)
        if (newValue is Traceable) newValue.applyPsiTrace(childValue)
    }
}

/**
 * Try to set property value by searching scalar node child with name
 * same as property name and converting it.
 */
context(Converter)
fun <T> MappingNode.convertChildScalar(
    property: KProperty0<T>,
    convertValue: Scalar.() -> T?,
) = convertChild(property) {
    value?.asScalarNode()?.let(convertValue)
}

/**
 * Try to set string property value by searching scalar node child with name
 * same as property name.
 */
context(Converter)
fun MappingNode.convertChildString(property: KProperty0<String?>) =
    convertChildScalar(property) { textValue }

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
context(Converter)
fun MappingNode.convertChildBoolean(property: KProperty0<Boolean?>) =
    convertChildScalar(property) { textValue.toBooleanStrictOrNull() }

/**
 * Try to set enum property value by searching scalar node child with name
 * same as property name.
 */
context(Converter)
fun <T : Enum<T>> MappingNode.convertChildEnum(
    property: KProperty0<T?>,
    enumIndex: EnumMap<T, String>,
    isFatal: Boolean = false,
    isLong: Boolean = false,
) = convertChildScalar(property) {
    convertEnum(enumIndex, isFatal = isFatal, isLong = isLong)
}

/**
 * Try to set collection property value by searching sequence node child with name
 * same as property name and converting its children.
 */
context(Converter)
fun <T> MappingNode.convertChildCollection(
    property: KProperty0<List<T>?>,
    convertValue: PsiElement.() -> T?,
) = convertChild(property) {
    val sequence = value?.asSequenceNode()
    sequence?.items?.mapNotNull(convertValue)
}

context(Converter)
fun <T> MappingNode.convertChildCollectionOfMappings(
    property: KProperty0<List<T>?>,
    convertValue: MappingNode.() -> T?,
) = convertChild(property) {
    val sequence = value?.asSequenceNode()
    sequence?.items?.mapNotNull { it.asMappingNode() }?.mapNotNull(convertValue)
}

/**
 * Try to set collection property value by searching scalar sequence node child with name
 * same as property name and converting its children.
 */
context(Converter)
fun <T> MappingNode.convertChildScalarCollection(
    property: KProperty0<List<T>?>,
    convertValue: Scalar.() -> T?,
) = convertChildCollection(property) {
    asScalarNode()?.let(convertValue)
}

/**
 * Try to find all matching child nodes and then map their values.
 */
context(Converter)
fun <T> MappingNode.convertModifierAware(
    property: KProperty0<Map<Modifiers, T>?>,
    noModifiersEntry: T? = null,
    convertValue: MappingEntry.() -> T?,
) {
    val newValue = TraceableMap<Modifiers, T>()
    if (noModifiersEntry != null) newValue.put(noModifiers, noModifiersEntry)
    keyValues
        .filter { it.keyText.orEmpty().startsWith(property.name)
                || it.keyText.orEmpty().startsWith(getAltName(property.name))}
        .forEach {
            val modifiers = it.extractModifiers()
            // Skip those that we failed to convert.
            convertValue(it)?.let { v ->
                newValue.put(modifiers, v)
            }
        }
    property.valueBase?.invoke(newValue)
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