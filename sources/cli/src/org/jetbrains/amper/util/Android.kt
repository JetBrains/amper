/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import org.jetbrains.amper.frontend.schema.AndroidSettings
import java.nio.file.Path
import java.nio.file.Paths

val AndroidSettings.repr: String
    get() = "AndroidSettings(minSdk=$minSdk, maxSdk=$maxSdk, targetSdk=$targetSdk, compileSdk=$compileSdk, namespace='$namespace', applicationId='$applicationId')"

class AndroidSdkDetector(
    private val suggesters: List<Suggester> = buildList {
        add(EnvironmentVariableSuggester("ANDROID_HOME"))
        add(EnvironmentVariableSuggester("ANDROID_SDK_ROOT"))
        add(SystemPropertySuggester("android.home"))
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

    class SystemPropertySuggester(private val propertyName: String) : Suggester {
        override fun suggestSdkPath(): Path? = System.getProperty(propertyName)?.let { Paths.get(it) }
    }

    class DefaultSuggester : Suggester {
        override fun suggestSdkPath(): Path? = System
            .getProperty("user.home")
            ?.let { Paths.get(it).resolve(".android-sdk") }

    }
}