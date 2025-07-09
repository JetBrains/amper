/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

interface SchemaEnumDeclaration : SchemaTypeDeclaration {
    val entries: List<EnumEntry>
    val isOrderSensitive: Boolean
    fun toEnumConstant(name: String): Any

    data class EnumEntry(
        val name: String,
        val schemaValue: String = name,
        val isOutdated: Boolean = false,
        val isIncludedIntoJsonSchema: Boolean = true,
        val documentation: String? = null,
    )
}