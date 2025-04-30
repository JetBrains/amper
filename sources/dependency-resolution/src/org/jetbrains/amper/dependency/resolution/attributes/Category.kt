/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.attributes

import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant

sealed class Category(override val value: String) : AttributeValue {
    companion object : Attribute<Category> {
        override val name: String = "org.gradle.category"

        override fun fromString(value: String): Category = when (value) {
            Library.value -> Library
            Documentation.value -> Documentation
            Platform.value -> Platform
            else -> Other(value)
        }
    }

    object Library : Category("library")
    object Documentation : Category("documentation")
    object Platform : Category("platform")
    class Other(value: String) : Category(value)
}

internal fun Variant.isDocumentation() = getAttributeValue(Category) == Category.Documentation
