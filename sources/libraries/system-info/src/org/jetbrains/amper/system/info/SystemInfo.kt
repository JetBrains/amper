/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.system.info

enum class OsFamily(internal val value: String) {
    Windows("windows"),
    Linux("linux"),
    MacOs("macos"),
    FreeBSD("freebsd"),
    Solaris("sunos");

    companion object {
        val current by lazy {
            val osName = System.getProperty("os.name")
            when {
                osName.startsWith("Linux") -> Linux
                osName.startsWith("Mac") || osName.startsWith("Darwin") -> MacOs
                osName.startsWith("Windows") -> Windows
                osName.startsWith("Solaris") || osName.startsWith("SunOS") -> Solaris
                osName.startsWith("FreeBSD") -> FreeBSD
                else -> error("Could not determine OS family from os.name '$osName'")
            }
        }
    }

    val isLinux by lazy { this == Linux }

    val isMac by lazy { this == MacOs }

    val isWindows by lazy { this == Windows }
}

enum class Arch(internal val value: String) {
    X64("x64"),
    Arm64("arm64");

    companion object {
        val current by lazy {
            when (val arch = System.getProperty("os.arch").lowercase().trim()) {
                "aarch64", "arm64", "armv8" -> Arm64
                "amd64", "x64", "x86-64", "x86_64", "x86lx64" -> X64
                else -> error("Unsupported architecture: $arch")
            }
        }
    }
}

interface SystemInfo {

    /**
     * The family of this operating system (Windows, Linux, macOS, etc.).
     */
    val family: OsFamily

    /**
     * The hardware architecture of this system.
     */
    val arch: Arch

    /**
     * A string combining the [family] and [arch] values, e.g. "linux-x64".
     */
    // TODO this is actually specific to how Compose artifacts are defined, and should be moved accordingly
    val familyArch: String
        get() = "${family.value.lowercase()}-${arch.value.lowercase()}"

    object CurrentHost : SystemInfo {
        override val family: OsFamily by lazy { OsFamily.current }
        override val arch: Arch by lazy { Arch.current }
    }
}
