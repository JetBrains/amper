/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import org.jetbrains.amper.frontend.schema.AndroidSettings

val AndroidSettings.repr: String
    get() = "AndroidSettings(minSdk=$minSdk, maxSdk=$maxSdk, targetSdk=$targetSdk, compileSdk=$compileSdk, namespace='$namespace', applicationId='$applicationId')"