/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.customTaskSchema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.SchemaDoc

@SchemaDoc("Defines source set type for custom tasks")
enum class CustomTaskSourceSetType(
    val value: String,
): SchemaEnum {

    @SchemaDoc("Add to sources")
    SOURCES(
        "sources",
    ),

    @SchemaDoc("Add to resources root")
    RESOURCES(
        "resources",
    );

    override fun toString() = value
    override val schemaValue: String = value
    override val outdated: Boolean = false

    companion object : EnumMap<CustomTaskSourceSetType, String>(CustomTaskSourceSetType::values, CustomTaskSourceSetType::value)
}
