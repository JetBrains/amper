/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import org.jetbrains.amper.frontend.api.SchemaNode
import kotlin.reflect.full.createType

inline fun <reified T> SchemaTypingContext.getType() = getType(T::class.createType())

inline fun <reified T : SchemaNode> SchemaTypingContext.getDeclaration() =
    (getType<T>() as SchemaType.ObjectType).declaration