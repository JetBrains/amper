/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.api.Default
import org.jetbrains.amper.frontend.api.SchemaNode
import kotlin.reflect.KClass

inline fun <reified T : SchemaNode> SchemaTypeDeclaration.isSameAs(): Boolean = isSameAs(T::class)

fun SchemaTypeDeclaration.isSameAs(`class`: KClass<out SchemaNode>): Boolean = qualifiedName == `class`.qualifiedName

fun SchemaObjectDeclaration.toType() = SchemaType.ObjectType(this)

fun SchemaVariantDeclaration.toType() = SchemaType.VariantType(this)

/**
 * Whether the value for this property must be present in the tree.
 * This is true for any property that has a traceable default value.
 *
 * Note that this is orthogonal to whether the property has a nullable type. Nullable properties don't necessarily have
 * a default value. If they do, the default is not necessarily null. If they don't, they are required despite being
 * nullable.
 */
fun SchemaObjectDeclaration.Property.isValueRequired() = default !is Default.TransformedDependent<*, *>

fun SchemaType.render(
    includeSyntax: Boolean = true,
): String = buildString {
    when (this@render) {
        is SchemaType.BooleanType -> {
            append("boolean")
            if (includeSyntax) {
                append(""" ( "true" | "false" )""")
            }
        }
        is SchemaType.IntType -> append("integer")
        is SchemaType.PathType -> append("path")
        is SchemaType.StringType -> when (semantics) {
            null -> append("string")
            SchemaType.StringType.Semantics.MavenCoordinates -> append("maven-coordinates")
        }
        is SchemaType.ListType -> append("sequence [${elementType.render(false)}]")
        is SchemaType.MapType -> append("mapping {${keyType.render(false)} : ${valueType.render(false)}}")
        is SchemaType.EnumType -> {
            // TODO: Introduce a public-name concept?
            append(declaration.simpleName)
            if (includeSyntax) {
                declaration.entries.filter { !it.isOutdated && it.isIncludedIntoJsonSchema }.joinTo(
                    buffer = this,
                    separator = " | ",
                    prefix = " ( ",
                    postfix = " )",
                ) { it.schemaValue.quote() }
            }
        }
        is SchemaType.ObjectType -> {
            // TODO: Introduce a public-name concept?
            // e.g. Dependency ( string | { string: ( "exported" | DependencyScope | {:} } ) )
            append(declaration.simpleName)
            if (includeSyntax) {
                append(" ")
                fun appendPossibleSyntax() {
                    val possibleSyntax = buildList {
                        declaration.getBooleanShorthand()?.let {
                            add(it.name.quote())
                        }
                        declaration.getSecondaryShorthand()?.let {
                            when(val type = it.type) {
                                is SchemaType.EnumType -> type.declaration.entries.forEach { entry ->
                                    // Add enum values "inline" for enum shorthand
                                    add(entry.schemaValue.quote())
                                }
                                else -> add(type.render())
                            }
                        }
                        add("{..}")
                    }
                    if (possibleSyntax.size == 1) {
                        append(possibleSyntax[0])
                    } else {
                        possibleSyntax.joinTo(
                            buffer = this,
                            prefix = "( ",
                            postfix = " )",
                            separator = " | ",
                        )
                    }
                }
                val fromKeyProperty = declaration.getFromKeyAndTheRestNestedProperty()
                if (fromKeyProperty != null) {
                    append("( ")
                    val fromKeyPropertyType = fromKeyProperty.type.render(false)
                    append(fromKeyPropertyType).append(" | ").append(fromKeyPropertyType).append(": ")
                    appendPossibleSyntax()
                    append(" )")
                } else {
                    appendPossibleSyntax()
                }
            }
        }
        is SchemaType.VariantType -> {
            // TODO: Introduce a public-name concept?
            append(declaration.simpleName)
            if (includeSyntax) {
                declaration.variantTree.joinTo(
                    buffer = this,
                    separator = " | ",
                    prefix = "( ",
                    postfix = " )",
                ) { it.declaration.simpleName }
            }
        }
    }
    if (isMarkedNullable) append(" | null")
}

private fun String.quote() = '"' + this + '"'

fun <T : SchemaType> T.withNullability(
    isMarkedNullable: Boolean,
): T {
    if (isMarkedNullable == this.isMarkedNullable) {
        return this
    }
    val copy = when (this) {
        is SchemaType.ListType -> copy(isMarkedNullable = isMarkedNullable)
        is SchemaType.MapType -> copy(isMarkedNullable = isMarkedNullable)
        is SchemaType.ObjectType -> copy(isMarkedNullable = isMarkedNullable)
        is SchemaType.BooleanType -> copy(isMarkedNullable = isMarkedNullable)
        is SchemaType.EnumType -> copy(isMarkedNullable = isMarkedNullable)
        is SchemaType.IntType -> copy(isMarkedNullable = isMarkedNullable)
        is SchemaType.PathType -> copy(isMarkedNullable = isMarkedNullable)
        is SchemaType.StringType -> copy(isMarkedNullable = isMarkedNullable)
        is SchemaType.VariantType -> copy(isMarkedNullable = isMarkedNullable)
    }
    @Suppress("UNCHECKED_CAST")
    return copy as T
}