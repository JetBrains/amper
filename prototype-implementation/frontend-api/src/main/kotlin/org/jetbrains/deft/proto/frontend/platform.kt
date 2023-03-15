package org.jetbrains.deft.proto.frontend

/**
 * Enum, that describes the set of platform specific attributes that platform combines.
 */
enum class PlatformFamily(val parent: PlatformFamily? = null) {
    // Not sure for now, if backend needs that much granularity.
    // Will remove if unused, with [parent] field.
    NATIVE,
    IOS(NATIVE),
    ANDROID,
    JVM,
    JS,
}

/**
 * Enum, that describes the concrete platform the sources are built for.
 */
enum class Platform(
    val family: PlatformFamily
) {
    // ios
    IOS_ARM_X64(PlatformFamily.IOS),
    IOS_ARM_SIMULATOR(PlatformFamily.IOS),
    IOS_X_64(PlatformFamily.IOS),

    // android
    ANDROID(PlatformFamily.ANDROID),

    // jvm
    JVM(PlatformFamily.JVM),

    // js
    JS(PlatformFamily.JS),
}