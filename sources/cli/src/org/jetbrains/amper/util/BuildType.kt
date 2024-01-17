package org.jetbrains.amper.util

import AndroidBuildRequest

enum class BuildType {
    Debug, Release
}

val BuildType.toAndroidRequestBuildType
    get() = when (this) {
        BuildType.Debug -> AndroidBuildRequest.BuildType.Debug
        BuildType.Release -> AndroidBuildRequest.BuildType.Release
    }
