/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.ModifierAware
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.api.valueBase
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.superclasses

/**
 * A class to traverse schema.
 */
interface SchemaVisitor {

    /**
     * Visit schema class.
     */
    fun visitClas(klass: KClass<*>)

    /**
     * Visit non typed property.
     *
     * [default] - default value for this scalar
     */
    fun visitCommon(
        prop: KProperty<*>,
        type: KType,
        default: Default<*>?,
    )

    /**
     * Visit property with type of [SchemaNode], possibly with
     * modifiers ([Map] typed with values of [SchemaNode] type)
     *
     * [types] - collection of types, that can be assigned to this field
     */
    fun visitTyped(
        prop: KProperty<*>,
        type: KType,
        schemaNodeType: KType,
        types: Collection<KClass<*>>,
        modifierAware: Boolean,
    )
}

/**
 * Visitor, that visits all schema tree elements depth first (except custom).
 */
abstract class RecurringVisitor : SchemaVisitor {
    override fun visitClas(klass: KClass<*>) =
        visitSchema(klass, this)

    override fun visitTyped(
        prop: KProperty<*>,
        type: KType,
        schemaNodeType: KType,
        types: Collection<KClass<*>>,
        modifierAware: Boolean
    ) = types.forEach { visitClas(it) }
}

/**
 * Perform schema visiting using specified visitor.
 */
internal fun visitSchema(
    root: KClass<*>,
    visitor: SchemaVisitor,
) {
    val noArgCtor = root.constructors.firstOrNull { it.parameters.isEmpty() }
        ?: error("Non compatible schema type declaration: ${root.simpleName}") // TODO Add reporting

    val rootInstance = noArgCtor.call()

    // Careful about CCE.
    root.schemaDeclaredMemberProperties()
        .forEach {
            with(visitor) {
                val unwrappedType = it.unwrapValueTypeArg ?: return@forEach // TODO Handle non KClass return type.
                val schemaNodeType = unwrappedType.unwrapSchemaTypeOrNull()
                val propertyValue = it.valueBase(rootInstance) ?: it.get(rootInstance)
                val defaultValue = propertyValue.default
                val modifiersAware = it.annotations.any { ModifierAware::class.isInstance(it) }

                when {
                    schemaNodeType != null -> visitTyped(
                        it,
                        unwrappedType,
                        schemaNodeType,
                        schemaNodeType.possibleTypes,
                        modifiersAware,
                    )

                    else -> visitCommon(
                        it,
                        unwrappedType,
                        defaultValue as? Default<Any>,
                    )
                }
            }
        }
}

/**
 * Get all declared member properties in class hierarchy, limiting by [SchemaNode].
 */
fun KClass<*>.schemaDeclaredMemberProperties(): Sequence<KProperty1<Any, ValueBase<*>>> {
    val schemaClasses = generateSequence(listOf(this)) { roots ->
        roots.flatMap { root ->
            root.superclasses
                .filter { it.isSubclassOf(SchemaNode::class) }
                .filter { it != SchemaNode::class }
        }.takeIf { it.isNotEmpty() }
    }
    return schemaClasses
        .flatten()
        .flatMap { it.declaredMemberProperties }
        .filterIsInstance<KProperty1<Any, ValueBase<*>>>()
}

fun KClass<*>.schemaDeclaredMutableProperties() = schemaDeclaredMemberProperties().filterIsInstance<KMutableProperty1<Any, Any?>>()

/**
 * The effective type of this property.
 * This is the type wrapped in [ValueBase] (if this property is of type [ValueBase]), or just the type of the property.
 */
val KProperty<*>.unwrapValueTypeArg: KType?
    get() {
        // TODO Handle non KClass classifier.
        val kClassClassifier = returnType.classifier as? KClass<*> ?: return null
        return if (kClassClassifier.isSubclassOf(ValueBase::class)) {
            // We have either [SchemaValue] or [NullableSchemaValue] wrapper.
            returnType.arguments.first().type
        } else {
            returnType
            // Some other type, currently unsupported.
//            error("Not supported type: $kClassClassifier in property ${this.name}")
        }
    }

// TODO For now we will use sealed subclasses, but later
// maybe some registry need to be introduced.
val KType.possibleTypes
    get() = unwrapKClass.sealedSubclasses.takeIf { it.isNotEmpty() }
        ?: listOf(unwrapKClass)

inline val KType.unwrapKClassOrNull get() = classifier as? KClass<*>
inline val KType.unwrapKClass get() = classifier as KClass<*>

val KType.isSchemaNode get() = unwrapKClassOrNull?.isSubclassOf(SchemaNode::class) == true

val KType.isEnum get() = unwrapKClassOrNull?.isSubclassOf(Enum::class) == true
val KType.isString get() = unwrapKClassOrNull?.isSubclassOf(String::class) == true
val KType.isTraceableString get() = unwrapKClassOrNull?.isSubclassOf(TraceableString::class) == true
val KType.isTraceablePath get() = unwrapKClassOrNull?.isSubclassOf(TraceablePath::class) == true
val KType.isTraceableEnum get() = unwrapKClassOrNull?.isSubclassOf(TraceableEnum::class) == true
val KType.isBoolean get() = unwrapKClassOrNull?.isSubclassOf(Boolean::class) == true
val KType.isInt get() = unwrapKClassOrNull?.isSubclassOf(Int::class) == true
val KType.isPath get() = unwrapKClassOrNull?.isSubclassOf(Path::class) == true
val KType.isScalar get() = isEnum || isTraceableEnum || isString || isTraceableString || isBoolean || isInt || isPath || isTraceablePath


// FiXME Here we assume that collection type will have only one type argument, that
// generally is not true. Maybe need to add constraints of value<Type> methods.
val KType.isCollection get() = (classifier as? KClass<*>)?.isSubclassOf(Collection::class) ?: false
val KType.collectionType get() = arguments.first().type!!
val KType.collectionTypeOrNull get() = arguments.firstOrNull()?.type

// FiXME Here we assume that map type will have only two type arguments, that
// generally is not true. Maybe need to add constraints of value<Type> methods.
val KType.isMap get() = (classifier as? KClass<*>)?.isSubclassOf(Map::class) ?: false
val KType.mapValueType get() = arguments[1].type!!
val KType.mapValueTypeOrNull get() = arguments.getOrNull(1)?.type

/**
 * Tries to extract schema type from collection, map or just property.
 */
fun KType.unwrapSchemaTypeOrNull(): KType? = when {
    isSchemaNode -> this
    isMap -> mapValueType.unwrapSchemaTypeOrNull()
    isCollection -> collectionType.unwrapSchemaTypeOrNull()
    else -> null
}