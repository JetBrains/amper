/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution

import org.jetbrains.amper.dependency.resolution.metadata.json.module.Variant

/**
 * This enum contains leaf platforms, dependencies resolution could be requested for.
 *
 * I.e., it contains non-native leaf platforms defined by attribute 'org.jetbrains.kotlin.platform.type'
 * as well as all native leaf platforms
 * that could be defined as a value of attribute 'org.jetbrains.kotlin.native.target'.
 *
 * See https://gist.github.com/h0tk3y/41c73d1f822378f52f1e6cce8dcf56aa for more information
 */
enum class ResolutionPlatform(
    val type: PlatformType
) {
    JVM (PlatformType.JVM),
    ANDROID (PlatformType.ANDROID_JVM),
    COMMON (PlatformType.COMMON),
    JS (PlatformType.JS),
    WASM(PlatformType.WASM),

    // LINUX -> NATIVE
    LINUX_X64(PlatformType.NATIVE),
    LINUX_ARM64(PlatformType.NATIVE),

    // TVOS -> APPLE -> NATIVE
    TVOS_ARM64(PlatformType.NATIVE),
    TVOS_X64(PlatformType.NATIVE),
    TVOS_SIMULATOR_ARM64(PlatformType.NATIVE),

    // MACOS -> APPLE -> NATIVE
    MACOS_X64(PlatformType.NATIVE),
    MACOS_ARM64(PlatformType.NATIVE),

    // IOS -> APPLE -> NATIVE
    IOS_ARM64(PlatformType.NATIVE),
    IOS_SIMULATOR_ARM64(PlatformType.NATIVE),
    IOS_X64(PlatformType.NATIVE),

    // WATCHOS -> APPLE -> NATIVE
    WATCHOS_ARM64(PlatformType.NATIVE),
    WATCHOS_ARM32(PlatformType.NATIVE),
    WATCHOS_DEVICE_ARM64(PlatformType.NATIVE),
    WATCHOS_SIMULATOR_ARM64(PlatformType.NATIVE),

    // MINGW -> NATIVE
    MINGW_X64(PlatformType.NATIVE),

    // ANDROID_NATIVE -> NATIVE
    ANDROID_NATIVE_ARM32(PlatformType.NATIVE),
    ANDROID_NATIVE_ARM64(PlatformType.NATIVE),
    ANDROID_NATIVE_X64(PlatformType.NATIVE),
    ANDROID_NATIVE_X86(PlatformType.NATIVE);

    val nativeTarget: String? = if (type == PlatformType.NATIVE) name.lowercase() else null
}

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

    internal fun matches(variant: Variant) = variant.attributes["org.jetbrains.kotlin.platform.type"]?.let { it == this.value } ?: true

    internal fun matchesJvmEnvironment(variant: Variant): Boolean {
        val jvmEnvironment = variant.attributes["org.gradle.jvm.environment"]
        return (jvmEnvironment == null) || when (this) {
            JVM -> {
                jvmEnvironment == "standard-jvm"
            }
            ANDROID_JVM ->  {
                jvmEnvironment == "android"
            }
            else -> true
        }
    }
}