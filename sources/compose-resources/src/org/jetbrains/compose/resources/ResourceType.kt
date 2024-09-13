/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.compose.resources

internal enum class ResourceType(val typeName: String, val accessorName: String) {
    DRAWABLE("drawable", "drawable"),
    STRING("string", "string"),
    STRING_ARRAY("string-array", "array"),
    PLURAL_STRING("plurals", "plurals"),
    FONT("font", "font");

    override fun toString(): String = typeName

    companion object {
        fun fromString(str: String): ResourceType? =
            ResourceType.values().firstOrNull { it.typeName.equals(str, true) }
    }
}
