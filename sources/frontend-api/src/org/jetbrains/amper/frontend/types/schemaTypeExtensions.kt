/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.api.SchemaNode
import kotlin.reflect.KClass

fun SchemaTypeDeclaration.simpleName() = qualifiedName.substringAfterLast('.')

inline fun <reified T : SchemaNode> SchemaTypeDeclaration.isSameAs(): Boolean = qualifiedName == T::class.qualifiedName

fun SchemaTypeDeclaration.isSameAs(`class`: KClass<out SchemaNode>): Boolean = qualifiedName == `class`.qualifiedName

fun SchemaObjectDeclaration.toType() = SchemaType.ObjectType(this)

fun SchemaVariantDeclaration.toType() = SchemaType.VariantType(this)

/**
 * Whether this property must be specified explicitly. This is true for any property that doesn't have a default value.
 *
 * Note that this is orthogonal to whether the property has a nullable type. Nullable properties don't necessarily have
 * a default value. If they do, the default is not necessarily null. If they don't, they are required despite being
 * nullable.
 */
fun SchemaObjectDeclaration.Property.isValueRequired() = default == null

fun SchemaType.render(
    includePossibleValues: Boolean = true,
): String = buildString {
    when (this@render) {
        is SchemaType.BooleanType -> append("boolean")
        is SchemaType.IntType -> append("integer")
        is SchemaType.PathType -> append("path")
        is SchemaType.StringType -> append("string")
        is SchemaType.ListType -> append("list[ ${elementType.render(false)} ]")
        is SchemaType.MapType -> append("map {${SchemaType.KeyStringType.render(false)} : ${valueType.render(false)} }")
        is SchemaType.EnumType -> {
            // TODO: Introduce a public-name concept?
            append(declaration.simpleName())
            if (includePossibleValues) {
                declaration.entries.joinTo(
                    buffer = this,
                    separator = " | ",
                    prefix = "( ",
                    postfix = " )",
                ) { '"' + it.schemaValue + '"' }
            }
        }
        is SchemaType.ObjectType -> {
            // TODO: Introduce a public-name concept?
            append(declaration.simpleName())
            if (includePossibleValues) {
                append("{...}")
            }
        }
        is SchemaType.VariantType -> {
            // TODO: Introduce a public-name concept?
            append(declaration.simpleName())
            if (includePossibleValues) {
                declaration.variantTree.joinTo(
                    buffer = this,
                    separator = " | ",
                    prefix = "( ",
                    postfix = " )",
                ) { it.declaration.simpleName() }
            }
        }
    }
    if (isMarkedNullable) append('?')
}
