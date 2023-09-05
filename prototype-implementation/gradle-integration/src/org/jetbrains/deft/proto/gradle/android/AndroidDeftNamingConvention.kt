package org.jetbrains.deft.proto.gradle.android

import com.android.build.api.dsl.AndroidSourceSet
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.FragmentWrapper

@Suppress("UnstableApiUsage")
object AndroidDeftNamingConvention {

    context(AndroidAwarePart)
    val AndroidSourceSet.deftFragment: FragmentWrapper? get() =
        when (name) {
            "main" -> module.sharedPlatformFragment(Platform.ANDROID, false)
            "test" -> module.sharedPlatformFragment(Platform.ANDROID, true)
            else -> module.fragmentsByName[name]
        }
}