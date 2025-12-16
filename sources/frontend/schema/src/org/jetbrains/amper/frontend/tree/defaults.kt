/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.contexts.DefaultContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import java.io.File
import java.nio.file.Path

/**
 * Creates a default [KeyValue] for the given [property], if the property has the default value.
 */
internal fun createDefault(property: SchemaObjectDeclaration.Property): KeyValue? {
    val value = property.default?.toTreeValue(
        type = property.type,
    ) ?: return null
    return KeyValue(DefaultTrace, value, property, DefaultTrace)
}

private fun createDefaultProperties(declaration: SchemaObjectDeclaration): List<KeyValue> =
    declaration.properties.mapNotNull(::createDefault)

private fun Default<*>.toTreeValue(type: SchemaType): TreeNode? = when (this) {
    is Default.Static -> toTreeValue(type)
    is Default.NestedObject -> {
        check(type is SchemaType.ObjectType)
        MappingNode(createDefaultProperties(type.declaration), type, DefaultTrace, TypeLevelDefaultContexts)
    }
    is Default.DirectDependent -> ReferenceNode(listOf(property.name), type, DefaultTrace, TypeLevelDefaultContexts)
    is Default.TransformedDependent<*, *> -> {
        // FIXME: Not yet supported! Need to rethink this default kind and implement it in another way
        null
    }
}

private fun Default.Static<*>.toTreeValue(type: SchemaType): TreeNode {
    val value = value
    return if (value == null) {
        check(type.isMarkedNullable) { "Null default is specified for non-nullable $type" }
        NullLiteralNode(DefaultTrace, TypeLevelDefaultContexts)
    } else when (type) {
        is SchemaType.BooleanType -> BooleanNode(value as Boolean, type, DefaultTrace, TypeLevelDefaultContexts)
        is SchemaType.EnumType -> EnumNode(
            when (value) {
                is Enum<*> -> value.name
                is String -> value
                else -> error("Invalid enum default: $value")
            },
            type, DefaultTrace, TypeLevelDefaultContexts,
        )
        is SchemaType.IntType -> IntNode(value as Int, type, DefaultTrace, TypeLevelDefaultContexts)
        is SchemaType.PathType -> PathNode(
            when (value) {
                is Path -> value
                // TODO: These `File` values come from the Maven compat layer. Try to move the conversion up a level
                is File -> value.toPath()
                else -> error("Invalid path default: $value")
            }, type, DefaultTrace, TypeLevelDefaultContexts,
        )
        is SchemaType.StringType -> StringNode(
            // TODO: Remove this `TraceableString` check when defaults are reworked
            if (value is TraceableString) value.value else value as String,
            type, DefaultTrace, TypeLevelDefaultContexts,
        )
        is SchemaType.ListType -> {
            check(value is List<*>)
            check(value.isEmpty() || type.elementType is SchemaType.ScalarType) {
                "Non-empty lists as defaults are allowed only for lists with scalar element types"
            }
            val children = value.map { Default.Static(it).toTreeValue(type.elementType) }
            ListNode(children, type, DefaultTrace, TypeLevelDefaultContexts)
        }
        is SchemaType.MapType -> {
            check(value == emptyMap<Nothing, Nothing>()) {
                "Only an empty map is permitted as a default for a map property. " +
                        "If there are cases, you'll need to extend the implementation here"
            }
            MappingNode(emptyList(), type, DefaultTrace, TypeLevelDefaultContexts)
        }
        is SchemaType.ObjectType, is SchemaType.VariantType -> {
            error("Static defaults for object types are not supported")
        }
    }
}

private val TypeLevelDefaultContexts = listOf(DefaultContext.TypeLevel)