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

fun SchemaObjectDeclaration.hasShorthands() = properties.any { it.hasShorthand }

fun SchemaObjectDeclaration.Property.isValueRequired() = !type.isMarkedNullable && default == null