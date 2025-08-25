/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

sealed interface SchemaType {
    val isMarkedNullable: Boolean

    sealed interface ScalarType : SchemaType
    
    sealed interface TypeWithDeclaration : SchemaType {
        val declaration: SchemaTypeDeclaration
    }

    data class BooleanType(
        override val isMarkedNullable: Boolean = false,
    ) : ScalarType {
        override fun toString() = "boolean"
    }

    data class IntType(
        override val isMarkedNullable: Boolean = false,
    ) : ScalarType {
        override fun toString() = "int"
    }

    data class StringType(
        override val isMarkedNullable: Boolean = false,
        val isTraceableWrapped: Boolean = false,
        val knownStringValues: Set<String>? = null,
    ) : ScalarType {
        override fun toString() = "string"
    }

    data class PathType(
        override val isMarkedNullable: Boolean = false,
        val isTraceableWrapped: Boolean = false,
    ) : ScalarType {
        override fun toString() = "path"
    }

    data class EnumType(
        override val declaration: SchemaEnumDeclaration,
        val isTraceableWrapped: Boolean = false,
        override val isMarkedNullable: Boolean = false,
    ) : ScalarType, TypeWithDeclaration {
        override fun toString() = declaration.qualifiedName.substringAfterLast(".")
    }

    data class ObjectType(
        override val declaration: SchemaObjectDeclaration,
        override val isMarkedNullable: Boolean = false,
    ) : TypeWithDeclaration

    data class VariantType(
        override val declaration: SchemaVariantDeclaration,
        override val isMarkedNullable: Boolean = false,
    ) : TypeWithDeclaration

    data class ListType(
        val elementType: SchemaType,
        override val isMarkedNullable: Boolean = false,
    ) : SchemaType

    data class MapType(
        val keyType: StringType,
        val valueType: SchemaType,
        override val isMarkedNullable: Boolean = false,
    ) : SchemaType
}