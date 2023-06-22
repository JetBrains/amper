package org.jetbrains.deft.proto.gradle.kmpp

import org.jetbrains.deft.proto.frontend.LeafFragment
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.pretty
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.LeafFragmentWrapper
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
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
    val Platform.targetName
        get() = pretty

    context(KMPEAware)
    val LeafFragmentWrapper.targetName
        get() = platform.targetName

    context(KMPEAware)
    val LeafFragmentWrapper.target
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

    private val LeafFragment.compilationName: String
        get() = when {
            isDefault && isTest -> "test"
            isDefault -> "main"
            else -> name
        }

    context(KotlinTarget)
    val LeafFragment.compilation
        get() = compilations.findByName(compilationName)

    context(KotlinTarget)
    fun LeafFragment.maybeCreateCompilation(block: KotlinCompilation<*>.() -> Unit) =
        compilations.maybeCreate(compilationName).apply(block)

}