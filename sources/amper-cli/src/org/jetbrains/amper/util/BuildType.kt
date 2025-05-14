/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import org.jetbrains.amper.android.AndroidBuildRequest

enum class BuildType(val value: String) {
    Debug("debug"),
    Release("release");
}

val BuildType.toAndroidRequestBuildType
    get() = when (this) {
        BuildType.Debug -> AndroidBuildRequest.BuildType.Debug
        BuildType.Release -> AndroidBuildRequest.BuildType.Release
    }
