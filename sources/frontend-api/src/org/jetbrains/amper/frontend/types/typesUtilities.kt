/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TraceableValue
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf


/**
 * Get all declared member properties in class hierarchy, limiting by [SchemaNode].
 */
fun KClass<*>.allSchemaProperties(): List<KProperty1<Any, Any?>> = allSuperclassesAndMe
    .filter { it.isSubclassOf(SchemaNode::class) }.minus(SchemaNode::class)
    .flatMap { it.declaredMemberProperties }
    .filterIsInstance<KProperty1<Any, Any?>>()

// TODO For now we will use sealed subclasses, but later some registry need to be introduced.
// Iterating over all sealed subtypes.
val KType.isSealed get() = kClassOrNull?.isSealed == true
val KType.allSealedSubclasses get() = kClass.allSealedSubclasses
val KClass<*>.allSealedSubclasses get() = buildList { addAllSealedSubclassesOf(this@allSealedSubclasses) }

private fun MutableList<KClass<*>>.addAllSealedSubclassesOf(klass: KClass<*>) {
    val (sealed, notSealed) = klass.sealedSubclasses.partition { it.isSealed }
    addAll(notSealed)
    sealed.forEach { addAllSealedSubclassesOf(it) }
}

// [KClass] access.
val KType.kClassOrNull get() = classifier as? KClass<*>
val KType.kClass get() = kClassOrNull!!

// Type checks.
val KType.isSchemaNode get() = isSubclassOf<SchemaNode>()
val KType.isString get() = isSubclassOf<String>()
val KType.isTraceableString get() = isSubclassOf<TraceableString>()
val KType.isTraceableEnum get() = isSubclassOf<TraceableEnum<*>>()
val KType.isBoolean get() = isSubclassOf<Boolean>()
val KType.isInt get() = isSubclassOf<Int>()
val KType.isPath get() = isSubclassOf<Path>()
val KType.isTraceablePath get() = isSubclassOf<TraceablePath>()
val KType.isEnum get() = isSubclassOf<Enum<*>>() && isSubclassOf<SchemaEnum>()
val KType.isScalar get() = isEnum || isTraceableEnum || isString || isTraceableString || isBoolean || isInt || isPath || isTraceablePath
val KType.isCollection get() = isSubclassOf<Collection<*>>()
val KType.isMap get() = isSubclassOf<Map<*, *>>()
val KType.isTraceableValue get() = isSubclassOf<TraceableValue<*>>()

// Type arguments accessors.
val KType.traceableType get() = arguments.first().type!!
val KType.collectionType get() = arguments.first().type!!
val KType.mapKeyType get() = arguments[0].type!!
val KType.mapValueType get() = arguments[1].type!!
val KType.unwrapTraceableValue get() = if (isTraceableValue) traceableType else this

val KType.enumValuesOrNull: List<SchemaEnum>? get() = annotatedEnumValuesOrNull?.map { it.first }
val KType.annotatedEnumValuesOrNull get() = kClassOrNull?.java?.fields?.filter { it.type.isEnum }
    ?.map { (it.get(kClass.java) as SchemaEnum) to it.annotations.orEmpty() }

inline fun <reified T> KType.isSubclassOf() = kClassOrNull.isSubclassOf(T::class)
fun KClass<*>?.isSubclassOf(other: KClass<*>) = this === other || this?.isSubclassOf(other) == true
private val KClass<*>.allSuperclassesAndMe get() = listOf(this) + allSuperclasses