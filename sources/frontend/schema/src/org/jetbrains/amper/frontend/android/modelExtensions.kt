/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.android

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Platform

/**
 * Returns the fragment where AndroidManifest.xml should be found.
 */
fun LeafFragment.findAndroidManifestFragment(): Fragment {
    require(platform == Platform.ANDROID) { "Non-android fragment" }

    // This is the case when the whole fragment tree is a simple 2-element bamboo:
    // `common` <- `android`. Then we allow placing AndroidManifest in `src` directory.
    return if (module.rootFragment.platforms.firstOrNull() == Platform.ANDROID) {
        module.rootFragment
    } else this
}
