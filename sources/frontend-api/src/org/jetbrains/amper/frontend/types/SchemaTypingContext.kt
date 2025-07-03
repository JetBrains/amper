/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

import kotlin.reflect.KType

/**
 * Container for [SchemaType]s.
 */
interface SchemaTypingContext {
    fun getType(type: KType): SchemaType
}
