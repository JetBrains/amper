package org.jetbrains.amper.resolver

import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.frontend.Platform

fun Platform.toResolutionPlatform() : ResolutionPlatform? = when(this) {
    Platform.JS -> ResolutionPlatform.JS
    Platform.JVM -> ResolutionPlatform.JVM
    Platform.ANDROID -> ResolutionPlatform.ANDROID
    Platform.WASM -> ResolutionPlatform.WASM
    Platform.LINUX_X64 -> ResolutionPlatform.LINUX_X64
    Platform.LINUX_ARM64 -> ResolutionPlatform.LINUX_ARM64
    Platform.TVOS_ARM64 -> ResolutionPlatform.TVOS_ARM64
    Platform.TVOS_X64 -> ResolutionPlatform.TVOS_X64
    Platform.TVOS_SIMULATOR_ARM64 -> ResolutionPlatform.TVOS_SIMULATOR_ARM64
    Platform.MACOS_X64 -> ResolutionPlatform.MACOS_X64
    Platform.MACOS_ARM64 -> ResolutionPlatform.MACOS_ARM64
    Platform.IOS_ARM64 -> ResolutionPlatform.IOS_ARM64
    Platform.IOS_SIMULATOR_ARM64 -> ResolutionPlatform.IOS_SIMULATOR_ARM64
    Platform.IOS_X64 -> ResolutionPlatform.IOS_X64
    Platform.WATCHOS_ARM64 -> ResolutionPlatform.WATCHOS_ARM64
    Platform.WATCHOS_ARM32 -> ResolutionPlatform.WATCHOS_ARM32
    Platform.WATCHOS_DEVICE_ARM64 -> ResolutionPlatform.WATCHOS_DEVICE_ARM64
    Platform.WATCHOS_SIMULATOR_ARM64 -> ResolutionPlatform.WATCHOS_SIMULATOR_ARM64
    Platform.MINGW_X64 -> ResolutionPlatform.MINGW_X64
    Platform.ANDROID_NATIVE_ARM32 -> ResolutionPlatform.ANDROID_NATIVE_ARM32
    Platform.ANDROID_NATIVE_ARM64 -> ResolutionPlatform.ANDROID_NATIVE_ARM64
    Platform.ANDROID_NATIVE_X64 -> ResolutionPlatform.ANDROID_NATIVE_X64
    Platform.ANDROID_NATIVE_X86 -> ResolutionPlatform.ANDROID_NATIVE_X86

    // DR could not be run for non-leaf platforms
    Platform.COMMON,
    Platform.NATIVE,
    Platform.LINUX,
    Platform.MINGW,
    Platform.ANDROID_NATIVE,
    Platform.APPLE,
    Platform.IOS,
    Platform.TVOS,
    Platform.MACOS,
    Platform.WATCHOS -> null
}