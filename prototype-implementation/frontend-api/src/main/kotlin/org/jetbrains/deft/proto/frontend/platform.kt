package org.jetbrains.deft.proto.frontend

/**
 * Enum, that describes the concrete platform the sources are built for.
 *
 * [parent] — Parent of the platform in natural KMP hierarchy.
 * [isLeaf] — Helper flag to indicate that the platform is a leaf in the hierarchy.
 */
enum class Platform(val parent: Platform? = null, val isLeaf: Boolean = false) {
    COMMON,

    JS(COMMON),
    JVM(COMMON),
    WASM(COMMON),
    ANDROID(COMMON),
    NATIVE(COMMON),

    LINUX(NATIVE),
    LINUX_X64(LINUX, isLeaf = true),
    LINUX_ARM64(LINUX, isLeaf = true),

    APPLE(NATIVE),

    TVOS(APPLE),
    TVOS_ARM64(TVOS, isLeaf = true),
    TVOS_X64(TVOS, isLeaf = true),
    TVOS_SIMULATOR_ARM64(TVOS, isLeaf = true),

    MACOS(APPLE),
    MACOS_X64(MACOS, isLeaf = true),
    MACOS_ARM64(MACOS, isLeaf = true),

    IOS(APPLE),
    IOS_ARM64(IOS, isLeaf = true),
    IOS_SIMULATOR_ARM64(IOS, isLeaf = true),
    IOS_X64(IOS, isLeaf = true),

    WATCHOS(APPLE),
    WATCHOS_ARM64(WATCHOS),
    WATCHOS_ARM32(WATCHOS),
    WATCHOS_DEVICE_ARM64(WATCHOS, isLeaf = true),
    WATCHOS_SIMULATOR_ARM64(WATCHOS, isLeaf = true),

    MINGW(NATIVE),
    MINGW_X64(MINGW, isLeaf = true),

    ANDROID_NATIVE(NATIVE),
    ANDROID_NATIVE_ARM32(ANDROID_NATIVE, isLeaf = true),
    ANDROID_NATIVE_ARM64(ANDROID_NATIVE, isLeaf = true),
    ANDROID_NATIVE_X64(ANDROID_NATIVE, isLeaf = true),
    ANDROID_NATIVE_X86(ANDROID_NATIVE, isLeaf = true),
}