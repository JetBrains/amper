/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.types.SchemaObjectDeclaration.Property

internal abstract class SchemaObjectDeclarationBase : SchemaObjectDeclaration {
    private val propertiesByName by lazy { properties.associateBy { it.name } }
    private val shorthands by lazy(::Shorthands)

    final override fun getProperty(name: String): Property? {
        return propertiesByName[name]
    }

    final override fun getBooleanShorthand(): Property? {
        return shorthands.boolean
    }

    final override fun getSecondaryShorthand(): Property? {
        return shorthands.secondary
    }

    final override fun getFromKeyAndTheRestNestedProperty(): Property? {
        return shorthands.fromKeyAndTheRestNestedProperty
    }

    private inner class Shorthands {
        val boolean: Property?
        val secondary: Property?
        val fromKeyAndTheRestNestedProperty: Property?

        init {
            val booleanShorthands = mutableListOf<Property>()
            val secondaryShorthands = mutableListOf<Property>()
            properties.filter { it.hasShorthand }.forEach { shorthand ->
                when (shorthand.type) {
                    is SchemaType.BooleanType -> booleanShorthands += shorthand
                    is SchemaType.EnumType,
                    is SchemaType.PathType,
                    is SchemaType.StringType,
                    is SchemaType.ListType,
                        -> secondaryShorthands += shorthand
                    else -> error("$this: Can't have shorthand property of type ${shorthand.type}")
                }
            }
            check(booleanShorthands.size <= 1) { "$this: Can't have more than one boolean shorthand property" }
            check(secondaryShorthands.size <= 1) { "$this: Can't have more than one non-boolean shorthand property" }

            boolean = booleanShorthands.firstOrNull()
            secondary = secondaryShorthands.firstOrNull()

            fromKeyAndTheRestNestedProperty = properties.filter { it.isFromKeyAndTheRestNested }.also {
                check(it.size <= 1) { "$this: Can't have more than one @FromKeyAndTheRestIsNested" }
            }.singleOrNull()?.also {
                check(it.type is SchemaType.StringType || it.type is SchemaType.PathType) {
                    "$this: @FromKeyAndTheRestIsNested can only be String or Path"
                }
            }
        }
    }
}

internal abstract class SchemaEnumDeclarationBase : SchemaEnumDeclaration {
    private val entriesBySchemaValue by lazy { entries.associateBy { it.schemaValue } }

    final override fun getEntryBySchemaValue(schemaValue: String): SchemaEnumDeclaration.EnumEntry? {
        return entriesBySchemaValue[schemaValue]
    }
}