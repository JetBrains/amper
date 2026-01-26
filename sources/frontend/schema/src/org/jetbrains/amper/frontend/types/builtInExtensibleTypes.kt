/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.types.SchemaType.TypeWithDeclaration
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf

/**
 * Typing context that allows to register custom properties and variants for reflection-based types.
 */
internal abstract class ExtensibleBuiltInTypingContext protected constructor(
    parent: SchemaTypingContext?,
    roots: List<KClass<*>>,
) : BuiltInTypingContext(parent, roots) {

    data class CustomPropertyDescriptor(
        val propertyName: String,
        val propertyType: SchemaType,
        val documentation: String?,
        val origin: SchemaOrigin,
        val default: Default<*>? = null,
        val canBeReferenced: Boolean = false,
        val isUserSettable: Boolean = true,
    )

    private val registeredCustomProperties = ConcurrentHashMap<DeclarationKey, MutableList<CustomPropertyDescriptor>>()

    /**
     * Adds a custom property to the type, referenced by [key].
     */
    internal fun addCustomProperty(key: DeclarationKey, descriptor: CustomPropertyDescriptor) =
        registeredCustomProperties.getOrPut(key) { mutableListOf() }.add(descriptor)

    override fun findOrRegisterTypeWithDeclaration(type: KType): TypeWithDeclaration? {
        val classifier = (type.classifier as? KClass<*>) ?: return super.findOrRegisterTypeWithDeclaration(type)

        val key = classifier.builtInKey

        // Try to check if the type has custom properties.
        if (registeredCustomProperties[key] != null) {
            // Must be a SchemaNode if it has custom properties
            val schemaNodeSubclass = classifier.asSubclassOf(SchemaNode::class)
            val declaration = findOrRegister<SchemaObjectDeclaration>(key) {
                BuiltinDeclarationWithCustomProperties(schemaNodeSubclass)
            } ?: return null

            return SchemaType.ObjectType(
                declaration = declaration,
                isMarkedNullable = type.isMarkedNullable,
            )
        }

        return super.findOrRegisterTypeWithDeclaration(type)
    }

    private open inner class BuiltinDeclarationWithCustomProperties<T : SchemaNode>(
        backingReflectionClass: KClass<T>,
    ) : SchemaObjectDeclaration, BuiltinClassDeclaration<T>(backingReflectionClass) {
        override val properties by lazy { parseBuiltInProperties() + customProperties(backingReflectionClass) }

        private fun customProperties(type: KClass<out SchemaNode>): List<SchemaObjectDeclaration.Property> =
            registeredCustomProperties[BuiltInKey(type)]?.map { custom ->
                SchemaObjectDeclaration.Property(
                    name = custom.propertyName,
                    type = custom.propertyType,
                    default = custom.default,
                    documentation = custom.documentation,
                    canBeReferenced = custom.canBeReferenced,
                    isUserSettable = custom.isUserSettable,
                    origin = custom.origin,
                    isPlatformAgnostic = true,
                )
            }.orEmpty()
    }
}

private fun <T : Any> KClass<*>.asSubclassOf(clazz: KClass<T>): KClass<out T> {
    check(isSubclassOf(clazz)) {
        "Class $qualifiedName is not a subclass of ${clazz.qualifiedName}"
    }
    @Suppress("UNCHECKED_CAST") // we checked right above
    return this as KClass<out T>
}
