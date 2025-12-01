/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.system.info

enum class OsFamily(val value: String) {
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
                else -> error("Could not determine OS family")
            }
        }
    }

    val isUnix by lazy { this != Windows }

    val isLinux by lazy { this == Linux }

    val isMac by lazy { this == MacOs }

    val isWindows by lazy { this == Windows }
}

enum class Arch(val value: String) {
    X64("x64"),
    Arm64("arm64");

    companion object {
        val current by lazy {
            when (val arch = System.getProperty("os.arch").lowercase().trim()) {
                "aarch64", "arm64" -> Arm64
                "x86_64", "x86-64", "amd64" -> X64
                else -> error("Unsupported architecture: $arch")
            }
        }
    }
}

interface SystemInfo {

    data class Os(
        val family: OsFamily,
        val version: String,
        val arch: Arch,
    ) {
        val familyArch get() = "${family.value.lowercase()}-${arch.value.lowercase()}"
    }

    fun detect(): Os {
        val osName = System.getProperty("os.name")
        var version = System.getProperty("os.version").lowercase()

        if (osName.startsWith("Windows") && osName.matches("Windows \\d+".toRegex())) {
            // for whatever reason, JRE reports "Windows 11" as a name and "10.0" as a version on Windows 11
            try {
                val version2 = osName.substring("Windows".length + 1) + ".0"
                if (version2.toFloat() > version.toFloat()) {
                    version = version2
                }
            } catch (ignored: NumberFormatException) {
            }
        }

        return Os(OsFamily.current, version, Arch.current)
    }
}

object DefaultSystemInfo : SystemInfo