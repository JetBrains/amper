/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.system.info.Arch
import org.jetbrains.amper.system.info.OsFamily
import org.jetbrains.amper.system.info.SystemInfo

object PlatformUtil {
    // Which platforms we may execute on current system
    // Always includes JVM
    // May include emulators or external devices if they're installed/connected to the system
    val platformsMayRunOnCurrentSystem: Set<Platform> by lazy {
        val set = buildSet {
            add(Platform.JVM)
            add(Platform.ANDROID)

            val os = SystemInfo.CurrentHost
            when (os.family) {
                OsFamily.Windows -> {
                    add(Platform.MINGW_X64)
                }
                OsFamily.Linux, OsFamily.FreeBSD, OsFamily.Solaris -> when (os.arch) {
                    Arch.X64 -> add(Platform.LINUX_X64)
                    Arch.Arm64 -> {
                        // Linux arm64 is not yet easily supported by kotlin native
                        // KT-36871 Support Aarch64 Linux as a host for the Kotlin/Native
                    }
                }
                OsFamily.MacOs -> when (os.arch) {
                    Arch.X64 -> {
                        add(Platform.MACOS_X64)
                        // Simulator targets:
                        add(Platform.IOS_X64)
                        add(Platform.TVOS_X64)
                    }
                    Arch.Arm64 -> {
                        add(Platform.MACOS_ARM64)
                        // Simulator targets:
                        add(Platform.IOS_SIMULATOR_ARM64)
                        add(Platform.TVOS_SIMULATOR_ARM64)
                        add(Platform.WATCHOS_SIMULATOR_ARM64)
                    }
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
