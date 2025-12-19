/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableEnum
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.contexts.DefaultContext
import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.stdlib.collections.associateByNotNull
import java.io.File
import java.nio.file.Path

/**
 * Creates a default [KeyValue] for the given [property], if the property has the default value.
 */
internal fun createDefault(property: SchemaObjectDeclaration.Property): RefinedKeyValue? {
    val default = property.default ?: return null
    val value = default.toTreeValue(property.type, DefaultTrace)
    return RefinedKeyValue(DefaultTrace, value, property, DefaultTrace)
}

private fun createDefaultProperties(declaration: SchemaObjectDeclaration): Map<String, RefinedKeyValue> =
    declaration.properties.associateByNotNull(
        keySelector = SchemaObjectDeclaration.Property::name,
        valueTransform = ::createDefault,
    )

internal fun Default.toTreeValue(type: SchemaType, trace: Trace): RefinedTreeNode = when (this) {
    is Default.Static -> toTreeValue(type, trace)
    is Default.NestedObject -> {
        check(type is SchemaType.ObjectType)
        RefinedMappingNode(createDefaultProperties(type.declaration), type, trace, TypeLevelDefaultContexts)
    }
    is Default.Reference -> ReferenceNode(referencedPath, type, transform, trace, TypeLevelDefaultContexts)
}

internal fun Default.Static.toTreeValue(type: SchemaType, trace: Trace): RefinedTreeNode {
    val value = value
    return if (value == null) {
        check(type.isMarkedNullable) { "Null default is specified for non-nullable $type" }
        NullLiteralNode(trace, TypeLevelDefaultContexts)
    } else when (type) {
        is SchemaType.BooleanType -> BooleanNode(value as Boolean, type, trace, TypeLevelDefaultContexts)
        is SchemaType.EnumType -> EnumNode(
            when (value) {
                // TODO: Remove this `TraceableEnum` check when defaults are reworked
                is TraceableEnum<*> -> value.value.name
                is Enum<*> -> value.name
                is String -> value
                else -> error("Invalid enum default: $value")
            },
            type, trace, TypeLevelDefaultContexts,
        )
        is SchemaType.IntType -> IntNode(value as Int, type, trace, TypeLevelDefaultContexts)
        is SchemaType.PathType -> PathNode(
            when (value) {
                is Path -> value
                // TODO: These `File` values come from the Maven compat layer. Try to move the conversion up a level
                is File -> value.toPath()
                else -> error("Invalid path default: $value")
            }, type, trace, TypeLevelDefaultContexts,
        )
        is SchemaType.StringType -> StringNode(
            // TODO: Remove this `TraceableString` check when defaults are reworked
            if (value is TraceableString) value.value else value as String,
            type, trace, TypeLevelDefaultContexts,
        )
        is SchemaType.ListType -> {
            check(value is List<*>)
            check(value.isEmpty() || type.elementType is SchemaType.ScalarType) {
                "Non-empty lists as defaults are allowed only for lists with scalar element types"
            }
            val children = value.map { Default.Static(it).toTreeValue(type.elementType, trace) }
            RefinedListNode(children, type, trace, TypeLevelDefaultContexts)
        }
        is SchemaType.MapType -> {
            check(value == emptyMap<Nothing, Nothing>()) {
                "Only an empty map is permitted as a default for a map property. " +
                        "If there are cases, you'll need to extend the implementation here"
            }
            RefinedMappingNode(emptyMap(), type, trace, TypeLevelDefaultContexts)
        }
        is SchemaType.ObjectType, is SchemaType.VariantType -> {
            error("Static defaults for object types are not supported")
        }
    }
}

private val TypeLevelDefaultContexts = listOf(DefaultContext.TypeLevel)