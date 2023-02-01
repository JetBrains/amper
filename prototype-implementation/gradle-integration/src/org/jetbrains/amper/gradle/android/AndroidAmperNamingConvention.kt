/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.android

import com.android.build.api.dsl.AndroidSourceSet
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.gradle.FragmentWrapper

@Suppress("UnstableApiUsage")
object AndroidAmperNamingConvention {

    context(AndroidAwarePart)
    val AndroidSourceSet.amperFragment: FragmentWrapper? get() =
        when (name) {
            "main" -> module.sharedPlatformFragment(Platform.ANDROID, false)
            "test" -> module.sharedPlatformFragment(Platform.ANDROID, true)
            "androidTest" -> null
            else -> module.fragmentsByName[name]
        }

    context(AndroidAwarePart)
    val FragmentWrapper.androidSourceSet: AndroidSourceSet? get() =
        when (name) {
            module.sharedPlatformFragment(Platform.ANDROID, false)?.name ->
                androidSourceSets?.findByName("main")
            module.sharedPlatformFragment(Platform.ANDROID, true)?.name ->
                androidSourceSets?.findByName("test")
            else -> androidSourceSets?.findByName(name)
        }
}