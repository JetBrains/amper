/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
            "main" -> leafPlatformFragments.first { it.isTest == false }
            "test" -> leafPlatformFragments.first { it.isTest == true }
            "androidTest" -> null
            else -> module.fragmentsByName[name]
        }

    context(AndroidAwarePart)
    val FragmentWrapper.androidSourceSet: AndroidSourceSet? get() =
        when (name) {
            leafPlatformFragments.first { it.isTest == false }.name -> androidSourceSets?.findByName("main")
            leafPlatformFragments.first { it.isTest == true }.name -> androidSourceSets?.findByName("test")
            else -> androidSourceSets?.findByName(name)
        }
}