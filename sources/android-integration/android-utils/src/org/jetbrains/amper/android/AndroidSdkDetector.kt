/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android

import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import java.nio.file.Path
import kotlin.io.path.Path

class AndroidSdkDetector(
    private val suggesters: List<Suggester> = buildList {
        add(EnvironmentVariableSuggester("ANDROID_HOME"))
        add(EnvironmentVariableSuggester("ANDROID_SDK_ROOT")) // old, deprecated variable but may be used
        add(DefaultSuggester())
    }
) {
    fun detectSdkPath(): Path? = suggesters.firstNotNullOfOrNull { it.suggestSdkPath() }

    interface Suggester {
        fun suggestSdkPath(): Path?
    }

    class EnvironmentVariableSuggester(private val environmentVariableName: String) : Suggester {
        override fun suggestSdkPath(): Path? = System.getenv(environmentVariableName)?.let { Path(it) }
    }

    class DefaultSuggester : Suggester {
        override fun suggestSdkPath(): Path = when (DefaultSystemInfo.detect().family) {
            OsFamily.Windows -> Path(System.getenv("LOCALAPPDATA")).resolve("Android/Sdk")
            OsFamily.MacOs -> Path(System.getProperty("user.home")).resolve("Library/Android/sdk")
            else -> Path(System.getProperty("user.home")).resolve("Android/Sdk")
        }
    }

    companion object {
        fun detectSdkPath() = AndroidSdkDetector().detectSdkPath()
    }
}
