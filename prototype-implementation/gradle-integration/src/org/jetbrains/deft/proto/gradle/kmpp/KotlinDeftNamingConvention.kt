package org.jetbrains.deft.proto.gradle.kmpp

import org.jetbrains.deft.proto.frontend.Artifact
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.TestArtifact
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.requireSingle
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

internal interface KMPEAware {
    val kotlinMPE: KotlinMultiplatformExtension
}

object KotlinDeftNamingConvention {

    context(KMPPBindingPluginPart)
    val KotlinSourceSet.deftFragment: FragmentWrapper?
        get() = fragmentsByName[name]

    context(KMPEAware)
    fun getTargetName(fragment: FragmentWrapper, platform: Platform) = when {
        platform == Platform.ANDROID && fragment.name == "main" ->
            "android"
        else ->
            fragment.name
                .replace("test", "main")
                .replace("Test", "")
    }

    context(KMPEAware)
    private val FragmentWrapper.targetName
        get() = getTargetName(
            this,
            platforms.requireSingle { "Leaf fragment must have exactly one platform" }
        )

    context(KMPEAware)
    val FragmentWrapper.target
        get() = kotlinMPE.targets.findByName(targetName)

    context(KMPEAware, SpecificPlatformPluginPart)
    val FragmentWrapper.kotlinSourceSetName: String
        get() = when (name) {
            leafNonTestFragment?.name -> "${leafNonTestFragment.targetName}Main"
            leafTestFragment?.name -> "${leafTestFragment.targetName}Test"
            else -> name
        }

    context(KMPEAware, SpecificPlatformPluginPart)
    val FragmentWrapper.kotlinSourceSet: KotlinSourceSet?
        get() = kotlinMPE.sourceSets.findByName(kotlinSourceSetName)

    private val Artifact.compilationName
        get() = when (this) {
            // todo: we need to create compilations by ourselves,
            //  so in future we will find compilation by artifact name
            is TestArtifact -> "test"
            else -> "main"
        }

    context(KotlinTarget)
    val Artifact.compilation
        get() = compilations.findByName(compilationName)

}