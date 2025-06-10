/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.EnumOrderSensitive
import org.jetbrains.amper.frontend.api.EnumValueFilter
import org.jetbrains.amper.frontend.api.PropertyMeta
import org.jetbrains.amper.frontend.api.SchemaNode
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType


/**
 * Container for [AmperType]s.
 */
abstract class AmperTypes {
    
    abstract operator fun get(type: KType): AmperType
    
    inline fun <reified T> aType() = this[T::class.starProjectedType]

    /**
     * Shortcut to get schema type descriptors.
     */
    inline operator fun <reified T : SchemaNode> invoke() = aType<T>() as Object

    // TODO Add overall types validation.
    sealed class AmperType(val kType: KType)

    open class Scalar(kType: KType) : AmperType(kType)

    // TODO Check that type is SchemaEnum.
    class Enum(kType: KType) : Scalar(kType) {
        private val unwrapped = kType.unwrapTraceableValue
        private val enumKlass = unwrapped.kClass as KClass<SchemaEnum>
        
        val annotatedEnumValues = unwrapped.annotatedEnumValuesOrNull.orEmpty().toList()
        val enumValues = annotatedEnumValues.map { it.first }
        
        val enumProperties = enumKlass.memberProperties
        
        val orderSensitive = enumKlass.findAnnotation<EnumOrderSensitive>()
        val valueFilter = enumKlass.findAnnotation<EnumValueFilter>()
        private val filterProp = valueFilter?.run { enumProperties.find { it.name == filterPropertyName } }
        val propertyFilter: (SchemaEnum) -> Boolean = { filterProp?.get(it) == valueFilter?.isNegated?.not() }
    }

    inner class Map(kType: KType) : AmperType(kType) {
        val valueType by lazy { this@AmperTypes[kType.mapValueType] }
    }

    inner class List(kType: KType) : AmperType(kType) {
        val valueType by lazy { this@AmperTypes[kType.collectionType] }
    }

    inner class Polymorphic(
        kType: KType,
        inheritors: () -> Iterable<KType>,
    ) : AmperType(kType) {
        val inheritors by lazy { inheritors().map { this@AmperTypes[it] as Object } }

        override fun toString() = "APolymorphic(${kType.kClass.qualifiedName})"
    }

    inner class Property(val meta: PropertyMeta) {
        val type by lazy { this@AmperTypes[meta.type] }
    }

    inner class Object(
        kType: KType,
        kProperties: () -> kotlin.collections.List<PropertyMeta>,
    ) : AmperType(kType) {
        val properties by lazy { kProperties().map { Property(it) } }

        // TODO Add validation, that aliases are not intersecting with any of other properties.
        val aliased by lazy {
            properties.flatMap { it.meta.nameAndAliases.asSequence() zip generateSequence { it } }.toMap()
        }

        val hasShorthands by lazy { properties.any { it.meta.hasShorthand } }

        override fun toString() = "AObject(${kType.kClass.qualifiedName})"
    }
}