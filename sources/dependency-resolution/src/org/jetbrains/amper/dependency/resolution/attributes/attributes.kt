/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.attributes

import org.jetbrains.amper.dependency.resolution.metadata.json.module.Dependency
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant

/**
 * Represents a Gradle attribute of a dependency or variant.
 *
 * See [Gradle documentation](https://docs.gradle.org/current/userguide/variant_attributes.html) for more details.
 */
interface Attribute<T : AttributeValue> {
    val name: String

    fun fromString(value: String): T
}

/**
 * Represents a value of a Gradle attribute.
 *
 * See [Gradle documentation](https://docs.gradle.org/current/userguide/variant_attributes.html) for more details.
 */
interface AttributeValue {
    val value: String
}

internal inline fun <reified T : AttributeValue> Dependency.getAttributeValue(attribute: Attribute<T>): T? =
    attributes[attribute.name]?.let(attribute::fromString)

internal inline fun <reified T : AttributeValue> Variant.getAttributeValue(attribute: Attribute<T>): T? =
    attributes[attribute.name]?.let(attribute::fromString)

internal inline fun <reified T : AttributeValue> Variant.hasNoAttribute(attribute: Attribute<T>): Boolean =
    attributes[attribute.name] == null
