/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

abstract class SchemaEnumDeclarationBase : SchemaEnumDeclaration {
    private val entriesBySchemaValue by lazy { entries.associateBy { it.schemaValue } }
    private val entriesByName by lazy { entries.associateBy { it.name } }

    final override fun getEntryBySchemaValue(schemaValue: String): SchemaEnumDeclaration.EnumEntry? {
        return entriesBySchemaValue[schemaValue]
    }

    final override fun getEntryByName(name: String): SchemaEnumDeclaration.EnumEntry? {
        return entriesByName[name]
    }
}