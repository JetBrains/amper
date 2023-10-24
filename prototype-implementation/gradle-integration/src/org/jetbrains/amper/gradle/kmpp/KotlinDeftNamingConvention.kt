package org.jetbrains.amper.gradle.kmpp

import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.pretty
import org.jetbrains.amper.gradle.FragmentWrapper
import org.jetbrains.amper.gradle.LeafFragmentWrapper
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
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
        get() = when(name) {
            "androidUnitTest" -> fragmentsByName["androidTest"]
            "commonMain" -> fragmentsByName["common"]
            else -> fragmentsByName[name]
        }

    context(KMPPBindingPluginPart)
    private val KotlinSourceSet.nearestDeftFragments: List<FragmentWrapper>
        get() = deftFragment?.let { listOf(it) }
            ?: dependsOn.flatMap { it.nearestDeftFragments }

    context(KMPPBindingPluginPart)
    val KotlinSourceSet.mostCommonNearestDeftFragment get() = nearestDeftFragments.let { nearest ->
        nearest.filter { it.fragmentDependencies.none { dep -> dep.target in nearest } }
            .maxByOrNull { it.platforms.size }
    }

    context(KMPEAware)
    val Platform.targetName
        get() = pretty

    context(KMPEAware)
    val LeafFragmentWrapper.targetName
        get() = platform.targetName

    context(KMPEAware)
    val LeafFragmentWrapper.target
        get() = kotlinMPE.targets.findByName(targetName)

    context(KMPEAware)
    val Platform.target
        get() = kotlinMPE.targets.findByName(targetName)

    context(KMPEAware)
    private val FragmentWrapper.commonKotlinSourceSetName: String
        get() = when {
            !isTest -> "commonMain"
            isTest -> "commonTest"
            else -> name
        }

    context(KMPEAware)
    val FragmentWrapper.kotlinSourceSetName: String
        get() = when(name) {
            // TODO Add variants support.
            "androidTest" -> "androidUnitTest"
            "common" -> "commonMain"
            else -> name
        }


    context(KMPEAware)
    val FragmentWrapper.kotlinSourceSet: KotlinSourceSet?
        get() = kotlinMPE.sourceSets.findByName(kotlinSourceSetName)

    context(KMPEAware)
    val FragmentWrapper.matchingKotlinSourceSets: List<KotlinSourceSet>
        get() = buildList {
            if (fragmentDependencies.none { it.type == FragmentDependencyType.REFINE }) {
                kotlinMPE.sourceSets.findByName(commonKotlinSourceSetName)?.let { add(it) }
            }
            kotlinMPE.sourceSets.findByName(kotlinSourceSetName)?.let { add(it) }
        }

    val LeafFragment.compilationName: String
        get() = when {
            isDefault && isTest -> "test"
            isDefault -> "main"
            else -> name
        }

    context(KotlinTarget)
    val LeafFragment.compilation: KotlinCompilation<KotlinCommonOptions>?
        get() {
            if (platform == Platform.ANDROID) {
                val androidCompilationName = buildString {
                    if (variants.contains("debug") || variants.isEmpty()) {
                        append("debug")
                    } else {
                        append("release")
                    }
                    if (isTest) {
                        append("UnitTest")
                    }
                }
                return compilations.findByName(androidCompilationName)
            }
            return compilations.findByName(compilationName)
        }

    context(KotlinTarget)
    fun LeafFragment.maybeCreateCompilation(block: KotlinCompilation<*>.() -> Unit): KotlinCompilation<KotlinCommonOptions>? =
        compilations.maybeCreate(compilationName).apply(block)
}