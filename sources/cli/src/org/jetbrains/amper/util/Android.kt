package org.jetbrains.amper.util

import org.jetbrains.amper.frontend.schema.AndroidSettings

val AndroidSettings.repr: String
    get() = "AndroidSettings(minSdk=$minSdk, maxSdk=$maxSdk, targetSdk=$targetSdk, compileSdk=$compileSdk, namespace='$namespace', applicationId='$applicationId')"