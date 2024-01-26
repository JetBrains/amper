/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import AndroidBuildRequest

enum class BuildType {
    Default, Debug, Release;

    val suffix: String get() {
        return when(this) {
            Default -> ""
            Debug, Release -> name
        }
    }
}

val BuildType.toAndroidRequestBuildType
    get() = when (this) {
        BuildType.Default -> AndroidBuildRequest.BuildType.Debug
        BuildType.Debug -> AndroidBuildRequest.BuildType.Debug
        BuildType.Release -> AndroidBuildRequest.BuildType.Release
    }
