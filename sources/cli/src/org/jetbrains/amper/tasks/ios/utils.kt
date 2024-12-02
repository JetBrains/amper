/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import com.jetbrains.cidr.xcode.plist.Plist
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.util.BuildType

val BuildType.variantName get() = value.lowercase().replaceFirstChar { it.titlecase() }

internal val Platform.architecture
    get() = when (this) {
        Platform.IOS_ARM64 -> "arm64"
        Platform.IOS_X64 -> "x86_64"
        Platform.IOS_SIMULATOR_ARM64 -> "arm64"
        else -> error("Cannot determine apple architecture for $this")
    }

internal val Platform.sdk
    get() = when (this) {
        Platform.IOS_ARM64 -> "iphoneos"
        Platform.IOS_SIMULATOR_ARM64, Platform.IOS_X64 -> "iphonesimulator"
        else -> error("Cannot determine apple platform for $this")
    }

internal fun Map<String, *>.toPlist(): Plist = Plist().also { plist ->
    for ((k, v) in this) {
        @Suppress("UNCHECKED_CAST")
        plist[k] = when (v) {
            is Map<*, *> -> (v as Map<String, *>).toPlist()
            else -> v
        }
    }
}
