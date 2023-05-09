package org.jetbrains.deft.proto.gradle.android

import com.android.build.api.dsl.AndroidSourceSet
import org.jetbrains.deft.proto.frontend.KotlinFragmentPart
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.part

@Suppress("UnstableApiUsage")
object AndroidDeftNamingConvention {

    context(AndroidBindingPluginPart)
    val AndroidSourceSet.deftFragment: FragmentWrapper? get() {
        if (name == "main") return leafNonTestAndroidFragment?.fragment
        if (name == "test") return leafTestAndroidFragment?.fragment
        return null
    }

    context(AndroidBindingPluginPart)
    val FragmentWrapper.androidSourceSet: AndroidSourceSet? get() {
        if (this.name == leafNonTestAndroidFragment?.fragment?.name)
            return androidSourceSets?.findByName("main")
        if (this.name == leafTestAndroidFragment?.fragment?.name)
            return androidSourceSets?.findByName("test")
        return null
    }

    context(AndroidBindingPluginPart)
    val FragmentWrapper.resPath
        get() = src?.resolve("res")?.toFile() ?: path.resolve("res").toFile()
}