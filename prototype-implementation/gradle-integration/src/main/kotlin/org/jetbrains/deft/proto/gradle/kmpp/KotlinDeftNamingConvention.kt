package org.jetbrains.deft.proto.gradle.kmpp

import org.jetbrains.deft.proto.frontend.Artifact
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.TestArtifact
import org.jetbrains.deft.proto.frontend.doCamelCase
import org.jetbrains.deft.proto.gradle.BindPlatform
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.android.AndroidAwarePart
import org.jetbrains.deft.proto.gradle.requireSingle
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

object KotlinDeftNamingConvention {

    context(KMPPBindingPluginPart)
    val KotlinSourceSet.deftFragment: FragmentWrapper?
        get() =
            fragmentsByName[name]

    context(KMPPBindingPluginPart)
    private val AndroidAwarePart.BindFragment.targetName
        get() = fragment.platforms
            .requireSingle { "Leaf android fragment must have exactly one platform" }
            .targetName

    context(KMPPBindingPluginPart)
    val FragmentWrapper.kotlinSourceSetName: String
        get() {
            if (name == leafNonTestAndroidFragment?.fragment?.name)
                return "${leafNonTestAndroidFragment.targetName}Main"
            if (name == leafTestAndroidFragment?.fragment?.name)
                return "${leafTestAndroidFragment.targetName}Test"
            return name
        }

    context(KMPPBindingPluginPart)
    val FragmentWrapper.kotlinSourceSet: KotlinSourceSet?
        get() =
            kotlinMPE.sourceSets.findByName(kotlinSourceSetName)

    context(KMPPBindingPluginPart)
    val Platform.targetName: String
        get() = name.doCamelCase()

    context(KMPPBindingPluginPart)
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