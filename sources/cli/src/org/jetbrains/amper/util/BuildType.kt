/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import AndroidBuildRequest
import org.jetbrains.amper.frontend.Platform

enum class BuildType(val value: String) {
    Debug("debug"),
    Release("release");

    fun suffix(platform: Platform?): String = if (platform == Platform.ANDROID) name else ""

    companion object {
        val buildTypeStrings: Set<String> = entries.map { it.value }.toSet()

        fun byValue(value: String): BuildType? = entries.associateBy { it.value }[value]
    }
}

val BuildType.toAndroidRequestBuildType
    get() = when (this) {
        BuildType.Debug -> AndroidBuildRequest.BuildType.Debug
        BuildType.Release -> AndroidBuildRequest.BuildType.Release
    }
