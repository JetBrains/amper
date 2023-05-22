package org.jetbrains.deft.proto.gradle.kmpp

import org.jetbrains.deft.proto.frontend.Artifact
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.TestArtifact
import org.jetbrains.deft.proto.frontend.doCamelCase
import org.jetbrains.deft.proto.gradle.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.android.AndroidAwarePart
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.java.JavaBindingPluginPart
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

    context(KMPPBindingPluginPart)
    private val FragmentWrapper.targetName
        get() = platforms
                .requireSingle { "Leaf android fragment must have exactly one platform" }
                .targetName

    context(KMPPBindingPluginPart, SpecificPlatformPluginPart)
    val FragmentWrapper.kotlinSourceSetName: String
        get() = when (name) {
            leafNonTestFragment?.name -> "${leafNonTestFragment.targetName}Main"
            leafTestFragment?.name -> "${leafTestFragment.targetName}Test"
            else -> name
        }

    context(KMPPBindingPluginPart, SpecificPlatformPluginPart)
    val FragmentWrapper.kotlinSourceSet: KotlinSourceSet?
        get() = kotlinMPE.sourceSets.findByName(kotlinSourceSetName)

    val Platform.targetName: String
        get() = name.doCamelCase()

    context(KMPEAware)
    val Platform.target
        get() = kotlinMPE.targets.findByName(targetName)

    private val Artifact.compilationName
        get() = when {
            this is TestArtifact && testFor.name == name -> "test"
            this is TestArtifact -> name
            else -> "main"
        }

    context(KotlinTarget)
    val Artifact.compilation
        get() = compilations.findByName(compilationName)

}