/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import org.jetbrains.amper.core.system.Arch
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.frontend.Platform

object PlatformUtil {
    // Which platforms we may execute on current system
    // Always includes JVM
    // May include emulators or external devices if they're installed/connected to the system
    val platformsMayRunOnCurrentSystem: Set<Platform> by lazy {
        val set = buildSet {
            add(Platform.JVM)
            add(Platform.ANDROID)

            when (OsFamily.current) {
                OsFamily.Windows -> {
                    add(Platform.MINGW_X64)
                }
                OsFamily.Linux, OsFamily.FreeBSD, OsFamily.Solaris -> when (DefaultSystemInfo.detect().arch) {
                    Arch.X64 -> add(Platform.LINUX_X64)
                    Arch.Arm64 -> {
                        // Linux arm64 is not yet easily supported by kotlin native
                        // KT-36871 Support Aarch64 Linux as a host for the Kotlin/Native
                    }
                }
                OsFamily.MacOs -> when (DefaultSystemInfo.detect().arch) {
                    Arch.X64 -> add(Platform.MACOS_X64)
                    Arch.Arm64 -> add(Platform.MACOS_ARM64)
                }
            }
        }

        set.forEach {
            check(it.isLeaf) {
                "Platform '$it' must be a leaf platform"
            }
        }

        set
    }
}
