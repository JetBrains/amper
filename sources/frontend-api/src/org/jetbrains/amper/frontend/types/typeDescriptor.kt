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
 * Container for [AType]s.
 */
abstract class ATypes {
    
    abstract operator fun get(type: KType): AType
    
    inline fun <reified T> aType() = this[T::class.starProjectedType]

    /**
     * Shortcut to get schema type descriptors.
     */
    inline operator fun <reified T : SchemaNode> invoke() = aType<T>() as AObject

    // TODO Add overall types validation.
    sealed class AType(val kType: KType)

    open inner class AScalar(kType: KType) : AType(kType)

    // TODO Check that type is SchemaEnum.
    inner class AEnum(kType: KType) : AScalar(kType) {
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

    inner class AMap(kType: KType) : AType(kType) {
        val valueType by lazy { this@ATypes[kType.mapValueType] }
    }

    inner class AList(kType: KType) : AType(kType) {
        val valueType by lazy { this@ATypes[kType.collectionType] }
    }

    inner class APolymorphic(
        kType: KType,
        inheritors: () -> Iterable<KType>,
    ) : AType(kType) {
        val inheritors by lazy { inheritors().map { this@ATypes[it] as AObject } }

        override fun toString() = "APolymorphic(${kType.kClass.qualifiedName})"
    }

    inner class AProperty(val meta: PropertyMeta) {
        val type by lazy { this@ATypes[meta.type] }
    }

    inner class AObject(
        kType: KType,
        kProperties: () -> List<PropertyMeta>,
    ) : AType(kType) {
        val properties by lazy { kProperties().map { AProperty(it) } }

        // TODO Add validation, that aliases are not intersecting with any of other properties.
        val aliased by lazy {
            properties.flatMap { it.meta.nameAndAliases.asSequence() zip generateSequence { it } }.toMap()
        }

        val hasShorthands by lazy { properties.any { it.meta.hasShorthand } }

        override fun toString() = "AObject(${kType.kClass.qualifiedName})"
    }
}