/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.ksp

import org.jetbrains.amper.frontend.Platform

enum class KspCompilationType(val kspMainClassFqn: String) {
    Common("com.google.devtools.ksp.cmdline.KSPCommonMain"),
    JVM("com.google.devtools.ksp.cmdline.KSPJvmMain"),
    JS("com.google.devtools.ksp.cmdline.KSPJsMain"),
    Native("com.google.devtools.ksp.cmdline.KSPNativeMain");

    companion object {
        fun forPlatform(platform: Platform): KspCompilationType = when (platform) {
            Platform.COMMON -> Common // TODO should we use common for native source sets with multiple targets?

            Platform.JS,
            Platform.WASM -> JS

            Platform.JVM,
            Platform.ANDROID -> JVM

            Platform.NATIVE,
            Platform.LINUX,
            Platform.LINUX_X64,
            Platform.LINUX_ARM64,
            Platform.APPLE,
            Platform.TVOS,
            Platform.TVOS_ARM64,
            Platform.TVOS_X64,
            Platform.TVOS_SIMULATOR_ARM64,
            Platform.MACOS,
            Platform.MACOS_X64,
            Platform.MACOS_ARM64,
            Platform.IOS,
            Platform.IOS_ARM64,
            Platform.IOS_SIMULATOR_ARM64,
            Platform.IOS_X64,
            Platform.WATCHOS,
            Platform.WATCHOS_ARM64,
            Platform.WATCHOS_ARM32,
            Platform.WATCHOS_DEVICE_ARM64,
            Platform.WATCHOS_SIMULATOR_ARM64,
            Platform.MINGW,
            Platform.MINGW_X64,
            Platform.ANDROID_NATIVE,
            Platform.ANDROID_NATIVE_ARM32,
            Platform.ANDROID_NATIVE_ARM64,
            Platform.ANDROID_NATIVE_X64,
            Platform.ANDROID_NATIVE_X86 -> Native
        }
    }
}