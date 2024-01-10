/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import com.sun.jna.Platform

object OS {
    val type: Type = when (val osType = Platform.getOSType()) {
        Platform.WINDOWS -> Type.Windows
        Platform.LINUX -> Type.Linux
        Platform.MAC -> Type.Mac
        else -> error("Unknown OS type $osType")
    }

    val isLinux: Boolean = type == Type.Linux

    val isMac: Boolean = type == Type.Mac

    val isWindows: Boolean = type == Type.Windows

    val isUnix: Boolean = type == Type.Mac || type == Type.Linux

    enum class Type {
        Windows,
        Linux,
        Mac,
    }
}
