/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.attributes

import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant

sealed class KotlinNativeTarget(override val value: String) : AttributeValue {
    companion object : Attribute<KotlinNativeTarget> {
        override val name: String = "org.jetbrains.kotlin.native.target"

        override fun fromString(value: String): KotlinNativeTarget {
            return ResolutionPlatform.entries.firstOrNull { it.nativeTarget == value }?.let(::Known) ?: Unknown(value)
        }
    }

    class Known(val platform: ResolutionPlatform) : KotlinNativeTarget(checkNotNull(platform.nativeTarget))
    class Unknown(value: String) : KotlinNativeTarget(value)
}

internal fun Variant.hasKotlinNativeTarget(platform: ResolutionPlatform): Boolean {
    val attribute = getAttributeValue(KotlinNativeTarget)
    return attribute is KotlinNativeTarget.Known && attribute.platform == platform
}
