/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

sealed interface SchemaType {
    val isMarkedNullable: Boolean

    /**
     * Type for [org.jetbrains.amper.frontend.tree.ScalarValue]
     */
    sealed interface ScalarType : SchemaType
    
    sealed interface TypeWithDeclaration : SchemaType {
        val declaration: SchemaTypeDeclaration
    }

    /**
     * Type for [org.jetbrains.amper.frontend.tree.StringInterpolationValue]
     */
    sealed interface StringInterpolatableType : ScalarType

    /**
     * Type for [org.jetbrains.amper.frontend.tree.MapLikeValue]
     */
    sealed interface MapLikeType : SchemaType

    data class BooleanType(
        override val isMarkedNullable: Boolean = false,
    ) : ScalarType

    data class IntType(
        override val isMarkedNullable: Boolean = false,
    ) : ScalarType

    data class StringType(
        override val isMarkedNullable: Boolean = false,
        val isTraceableWrapped: Boolean = false,
        val knownStringValues: Set<String>? = null,
        val semantics: Semantics? = null,
    ) : ScalarType, StringInterpolatableType {
        enum class Semantics {
            /**
             * String has maven coordinates format: <groupId>:<artifactId>(:<version>)?(:<qualifier>)?
             */
            MavenCoordinates,

            /**
             * FQN that references a class used as an entrypoint for JVM.
             */
            JvmMainClass,

            /**
             * FQN that references a class marked with `@Schema` annotation used as plugin settings class.
             */
            PluginSettingsClass,
        }
    }

    data class PathType(
        override val isMarkedNullable: Boolean = false,
        val isTraceableWrapped: Boolean = false,
    ) : ScalarType, StringInterpolatableType

    data class EnumType(
        override val declaration: SchemaEnumDeclaration,
        val isTraceableWrapped: Boolean = false,
        override val isMarkedNullable: Boolean = false,
    ) : ScalarType, TypeWithDeclaration

    data class ObjectType(
        override val declaration: SchemaObjectDeclaration,
        override val isMarkedNullable: Boolean = false,
    ) : TypeWithDeclaration, MapLikeType

    data class VariantType(
        override val declaration: SchemaVariantDeclaration,
        override val isMarkedNullable: Boolean = false,
    ) : TypeWithDeclaration

    data class ListType(
        val elementType: SchemaType,
        override val isMarkedNullable: Boolean = false,
    ) : SchemaType

    data class MapType(
        val valueType: SchemaType,
        val keyType: StringType = StringType,
        override val isMarkedNullable: Boolean = false,
    ) : SchemaType, MapLikeType

    companion object {
        val StringType = StringType()
        val TraceableStringType = StringType(isTraceableWrapped = true)
        val PathType = PathType()
        val BooleanType = BooleanType()
    }
}