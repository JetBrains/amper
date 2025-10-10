/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.foojay.model

/**
 * The operating system that a JDK is compatible with.
 */
enum class OperatingSystem(val apiValue: String, val libCType: LibCType) {
    LINUX("linux", LibCType.GLIBC),
    LINUX_MUSL("linux", LibCType.MUSL),
    FREE_BSD("free_bsd", LibCType.LIBC),
    MACOS("macos", LibCType.LIBC),
    WINDOWS("windows", LibCType.C_STD_LIB),
    SOLARIS("solaris", LibCType.LIBC),
    QNX("qnx", LibCType.LIBC),
    AIX("aix", LibCType.LIBC);

    companion object {

        /**
         * The [OperatingSystem] value representing the current system.
         */
        fun current(): OperatingSystem {
            val osName = System.getProperty("os.name").lowercase()
            return when {
                osName.startsWith("mac") || osName.startsWith("darwin") -> MACOS
                osName.startsWith("linux") -> if ("musl" in osName || "alpine" in osName) LINUX_MUSL else LINUX
                osName.startsWith("win") -> WINDOWS
                osName.startsWith("aix") -> AIX
                osName.startsWith("qnx") -> QNX
                osName.startsWith("solaris") || osName.startsWith("sunos") -> SOLARIS
                osName.startsWith("freebsd") -> FREE_BSD
                "musl" in osName || "alpine" in osName -> LINUX_MUSL
                else -> LINUX
            }
        }
    }
}
