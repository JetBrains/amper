package org.jetbrains.deft.proto.gradle.android

import com.android.build.api.dsl.AndroidSourceSet
import org.jetbrains.deft.proto.frontend.KotlinFragmentPart
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.part

@Suppress("UnstableApiUsage")
object AndroidDeftNamingConvention {

    context(AndroidAwarePart)
    val AndroidSourceSet.deftFragment: FragmentWrapper? get() = when(name) {
        "main" -> leafNonTestFragment
        "test" -> leafTestFragment
        else -> null
    }

    context(AndroidAwarePart)
    private val FragmentWrapper.androidResPath
        get() = src?.resolve("res")
            ?: path.resolve("res")

    context(AndroidAwarePart)
    val FragmentWrapper.androidResPaths get() = listOf(androidResPath)

}