/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core


internal enum class OS {
    WINDOWS,
    MACOSX,
    LINUX;

    companion object {
        val current: OS
            get() {
                val osName = System.getProperty("os.name").lowercase()
                return when {
                    osName.startsWith("mac") -> MACOSX
                    osName.startsWith("linux") -> LINUX
                    osName.startsWith("windows") -> WINDOWS
                    else -> throw IllegalStateException("Only Mac/Linux/Windows are supported now, current os: $osName")
                }
            }
    }
}