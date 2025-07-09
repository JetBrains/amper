/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.schema.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PluginData(
    val id: Id,
    val pluginModuleRoot: String,
    val moduleExtensionSchemaName: SchemaName? = null,
    val description: String? = null,
    val enumTypes: List<EnumData> = emptyList(),
    val classTypes: List<ClassData> = emptyList(),
    val tasks: List<TaskInfo> = emptyList(),
) {
    init {
        require(moduleExtensionSchemaName == null ||
                classTypes.any { it.name == moduleExtensionSchemaName })
    }

    @Serializable
    @JvmInline
    value class Id(
        val value: String,
    )

    @Serializable
    data class EnumData(
        val schemaName: SchemaName,
        val entries: List<Entry>,
    ) {
        @Serializable
        data class Entry(
            val name: String,
            val schemaName: String,
            val doc: String? = null,
        )
    }

    @Serializable
    data class ClassData(
        val name: SchemaName,
        val properties: List<Property> = emptyList(),
        val doc: String? = null,
    ) {
        @Serializable
        data class Property(
            val name: String,
            val type: Type,
            val doc: String? = null,
        )
    }


    @Serializable
    sealed interface Type {
        val isNullable: Boolean

        @Serializable
        @SerialName("boolean")
        data class BooleanType(
            override val isNullable: Boolean = false,
        ) : Type

        @Serializable
        @SerialName("int")
        data class IntType(
            override val isNullable: Boolean = false,
        ) : Type

        @Serializable
        @SerialName("string")
        data class StringType(
            override val isNullable: Boolean = false,
        ) : Type

        @Serializable
        @SerialName("path")
        data class PathType(
            override val isNullable: Boolean = false,
        ) : Type

        @Serializable
        @SerialName("enum")
        data class EnumType(
            val schemaName: SchemaName,
            override val isNullable: Boolean = false,
        ) : Type

        @Serializable
        @SerialName("map")
        data class MapType(
            val valueType: Type,
            override val isNullable: Boolean = false,
        ) : Type

        @Serializable
        @SerialName("list")
        data class ListType(
            val elementType: Type,
            override val isNullable: Boolean = false,
        ) : Type

        @Serializable
        @SerialName("object")
        data class ObjectType(
            val schemaName: SchemaName,
            override val isNullable: Boolean = false,
        ) : Type
    }
}