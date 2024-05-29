/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android

import com.android.SdkConstants
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import java.nio.file.Path
import java.nio.file.Paths

class AndroidSdkDetector(
    private val suggesters: List<Suggester> = buildList {
        add(EnvironmentVariableSuggester(SdkConstants.ANDROID_HOME_ENV))
        add(EnvironmentVariableSuggester(SdkConstants.ANDROID_SDK_ROOT_ENV))
        add(DefaultSuggester())
    }
) {
    fun detectSdkPath(): Path? = suggesters.firstNotNullOfOrNull { it.suggestSdkPath() }

    interface Suggester {
        fun suggestSdkPath(): Path?
    }

    class EnvironmentVariableSuggester(private val environmentVariableName: String) : Suggester {
        override fun suggestSdkPath(): Path? = System.getenv(environmentVariableName)?.let { Paths.get(it) }
    }

    class DefaultSuggester : Suggester {
        override fun suggestSdkPath(): Path? = when (DefaultSystemInfo.detect().family) {
            OsFamily.Windows -> Path.of(System.getenv("LOCALAPPDATA")).resolve("Android/Sdk")
            OsFamily.MacOs -> Path.of(System.getProperty("user.home")).resolve("Library/Android/sdk")
            else -> Path.of(System.getProperty("user.home")).resolve("Android/Sdk")
        }
    }

    companion object {
        fun detectSdkPath() = AndroidSdkDetector().detectSdkPath()
    }
}
