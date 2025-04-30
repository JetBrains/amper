/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.attributes

import org.jetbrains.amper.dependency.resolution.PlatformType
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant

sealed class KotlinPlatformType(override val value: String) : AttributeValue {
    companion object : Attribute<KotlinPlatformType> {
        override val name: String = "org.jetbrains.kotlin.platform.type"

        override fun fromString(value: String): KotlinPlatformType =
            PlatformType.entries.firstOrNull { it.value == value }?.let(::Known) ?: Unknown(value)
    }

    class Known(val platformType: PlatformType) : KotlinPlatformType(platformType.value)
    class Unknown(value: String) : KotlinPlatformType(value)
}

internal fun Variant.hasKotlinPlatformType(platform: PlatformType): Boolean {
    val attribute = getAttributeValue(KotlinPlatformType)
    return attribute is KotlinPlatformType.Known && attribute.platformType == platform
}
