package org.jetbrains.deft.proto.frontend

enum class ProductType(
    val value: String,
    val supportedPlatforms: Set<Platform>,
    val defaultPlatforms: Set<Platform>?
) {
    LIB(
        "lib",
        supportedPlatforms = Platform.leafPlatforms(),
        defaultPlatforms = null
    ),
    LEGACY_APP(
        "app",
        supportedPlatforms = Platform.leafPlatforms(),
        defaultPlatforms = setOf(Platform.JVM)
    ),
    JVM_APP(
        "jvm/app",
        supportedPlatforms = setOf(Platform.JVM),
        defaultPlatforms = setOf(Platform.JVM)
    ),
    ANDROID_APP(
        "android/app",
        supportedPlatforms = setOf(Platform.ANDROID),
        defaultPlatforms = setOf(Platform.ANDROID)
    ),
    MACOS_APP(
        "macos/app",
        supportedPlatforms = setOf(Platform.MACOS_X64, Platform.MACOS_ARM64),
        defaultPlatforms = setOf(Platform.MACOS_X64, Platform.MACOS_ARM64)
    ),
    LINUX_APP(
        "linux/app",
        supportedPlatforms = setOf(Platform.LINUX_X64, Platform.LINUX_ARM64),
        defaultPlatforms = setOf(Platform.LINUX_X64, Platform.LINUX_ARM64)
    );

    fun isLibrary(): Boolean {
        return this == LIB
    }

    override fun toString(): String {
        return value
    }

    companion object {
        fun findForValue(value: String): ProductType? {
            return entries.find {
                it.value == value
            }
        }
    }
}