/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.protobuf

import com.sun.jna.Platform

/**
 * Native system info provider utility.
 * TODO: Provide this as an API?
 */
class SystemInfo private constructor(
    val os: Os,
    val arch: Arch,
) {
    enum class Os(val string: String) {
        Windows("windows"),
        Linux("linux"),
        Mac("osx"),
    }

    enum class Arch(val string: String) {
        AArch64("aarch_64"),
        X86_64("x86_64"),
    }

    companion object {
        fun detect(): SystemInfo {
            val os = when(Platform.getOSType()) {
                Platform.LINUX -> Os.Linux
                Platform.MAC -> Os.Mac
                Platform.WINDOWS -> Os.Windows
                else -> error("Unsupported platform for protoc!")
            }
            val arch = when (val arch = Platform.ARCH) {
                "aarch64" -> Arch.AArch64
                "x86-64" -> Arch.X86_64
                else -> error("Unsupported architecture: $arch")
            }

            return SystemInfo(os, arch)
        }
    }
}