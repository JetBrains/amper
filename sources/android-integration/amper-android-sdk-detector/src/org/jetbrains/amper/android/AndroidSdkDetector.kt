/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import java.nio.file.Path
import kotlin.io.path.Path

object AndroidSdkDetector {

    @UsedInIdePlugin
    fun detectSdkPath(): Path = getPathFromEnv("ANDROID_HOME")
        ?: getPathFromEnv("ANDROID_SDK_ROOT") // old variable but may still be used
        ?: defaultSdkPath()

    private fun getPathFromEnv(envVarName: String): Path? = System.getenv(envVarName)?.let { Path(it) }

    private fun defaultSdkPath(): Path = when (DefaultSystemInfo.detect().family) {
        OsFamily.Windows -> Path(System.getenv("LOCALAPPDATA")).resolve("Android/Sdk")
        OsFamily.MacOs -> Path(System.getProperty("user.home")).resolve("Library/Android/sdk")
        else -> Path(System.getProperty("user.home")).resolve("Android/Sdk")
    }
}
