/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

sealed interface SchemaType {
    val isMarkedNullable: Boolean

    sealed interface ScalarType : SchemaType

    data class BooleanType(
        override val isMarkedNullable: Boolean = false,
    ) : ScalarType

    data class IntType(
        override val isMarkedNullable: Boolean = false,
    ) : ScalarType

    data class StringType(
        override val isMarkedNullable: Boolean = false,
        val isTraceableWrapped: Boolean = false,
    ) : ScalarType

    data class PathType(
        override val isMarkedNullable: Boolean = false,
        val isTraceableWrapped: Boolean = false,
    ) : ScalarType

    data class EnumType(
        val declaration: SchemaEnumDeclaration,
        val isTraceableWrapped: Boolean = false,
        override val isMarkedNullable: Boolean = false,
    ) : ScalarType

    data class ObjectType(
        val declaration: SchemaObjectDeclaration,
        override val isMarkedNullable: Boolean = false,
    ) : SchemaType

    data class VariantType(
        val declaration: SchemaVariantDeclaration,
        override val isMarkedNullable: Boolean = false,
    ) : SchemaType

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