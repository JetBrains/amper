/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.tree.RefinedKeyValue
import org.jetbrains.amper.frontend.tree.createDefault

abstract class SchemaObjectDeclarationBase : SchemaObjectDeclaration {
    private val propertiesByName by lazy { properties.associateBy { it.name } }
    private val shorthands by lazy(::Shorthands)
    private val defaults = mutableMapOf<String, RefinedKeyValue?>()

    final override fun getProperty(name: String): SchemaObjectDeclaration.Property? {
        return propertiesByName[name]
    }

    final override fun getBooleanShorthand(): SchemaObjectDeclaration.Property? {
        return shorthands.boolean
    }

    final override fun getSecondaryShorthand(): SchemaObjectDeclaration.Property? {
        return shorthands.secondary
    }

    final override fun getFromKeyAndTheRestNestedProperty(): SchemaObjectDeclaration.Property? {
        return shorthands.fromKeyAndTheRestNestedProperty
    }

    final override fun getDefaultFor(property: SchemaObjectDeclaration.Property): RefinedKeyValue? {
        require(propertiesByName[property.name] === property) {
            "Property doesn't belong to the `$this` class: $property"
        }
        return defaults.getOrPut(property.name) { createDefault(property) }
    }

    private inner class Shorthands {
        val boolean: SchemaObjectDeclaration.Property?
        val secondary: SchemaObjectDeclaration.Property?
        val fromKeyAndTheRestNestedProperty: SchemaObjectDeclaration.Property?

        init {
            val booleanShorthands = mutableListOf<SchemaObjectDeclaration.Property>()
            val secondaryShorthands = mutableListOf<SchemaObjectDeclaration.Property>()
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

            for (shorthand in (booleanShorthands + secondaryShorthands)) {
                check(shorthand.isUserSettable) { "shorthand property must be settable" }
            }

            boolean = booleanShorthands.firstOrNull()
            secondary = secondaryShorthands.firstOrNull()

            fromKeyAndTheRestNestedProperty = properties.filter { it.isFromKeyAndTheRestNested }.also {
                check(it.size <= 1) { "$this: Can't have more than one @FromKeyAndTheRestIsNested" }
            }.singleOrNull()?.also {
                check(it.type is SchemaType.StringType || it.type is SchemaType.PathType) {
                    "$this: @FromKeyAndTheRestIsNested can only be String or Path"
                }
                check(it.isUserSettable) { "'FromKeyAndTheRestIsNested' property must be settable" }
            }
        }
    }
}