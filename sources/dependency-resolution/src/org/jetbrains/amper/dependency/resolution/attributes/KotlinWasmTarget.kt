/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.attributes

import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant

sealed class KotlinWasmTarget(override val value: String) : AttributeValue {
    companion object : Attribute<KotlinWasmTarget> {
        override val name: String = "org.jetbrains.kotlin.wasm.target"

        override fun fromString(value: String): KotlinWasmTarget {
            return ResolutionPlatform.entries.firstOrNull { it.wasmTarget == value }
                ?.let(::Known) ?: Unknown(value)
        }
    }

    class Known(val platform: ResolutionPlatform) : KotlinWasmTarget(checkNotNull(platform.wasmTarget))
    class Unknown(value: String) : KotlinWasmTarget(value)
}

internal fun Variant.hasKotlinWasmTarget(platform: ResolutionPlatform): Boolean {
    val attribute = getAttributeValue(KotlinWasmTarget)
    return attribute is KotlinWasmTarget.Known && attribute.platform == platform
}
