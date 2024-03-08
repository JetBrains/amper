/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.metadata.json.Variant

/**
 * This enum contains possible values of module file attribute "org.jetbrains.kotlin.platform.type".
 * The list is taken from gradle-plugin class 'org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType'.
 *
 * See https://gist.github.com/h0tk3y/41c73d1f822378f52f1e6cce8dcf56aa for more information
 */
enum class PlatformType(
    val value: String,
    /**
     * If a variant for the platform is absent, then we try to find one for a fallback platform.
     */
    val fallback: PlatformType? = null,
) {
    JVM ("jvm"),
    ANDROID_JVM ("androidJvm", fallback = JVM),
    COMMON ("common"),
    JS ("js"),
    NATIVE ("native"),
    WASM("wasm");

    fun matches(variant: Variant) = variant.attributes["org.jetbrains.kotlin.platform.type"]?.let { it == this.value } ?: true
}