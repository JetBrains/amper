/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("DEPRECATION") // Deprecation for KotlinCommonOptions

package org.jetbrains.amper.gradle.kmpp

import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.gradle.FragmentWrapper
import org.jetbrains.amper.gradle.LeafFragmentWrapper
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

interface KMPEAware {
    val kotlinMPE: KotlinMultiplatformExtension
}

object KotlinAmperNamingConvention {
    context(KMPPBindingPluginPart)
    val KotlinSourceSet.amperFragment: FragmentWrapper?
        get() = fragmentsByKotlinSourceSetName[name]

    /**
     * The Amper fragment corresponding to this source set, or to the nearest source sets that this source set depends on.
     * We basically go down every path in the source set dependency tree until we find source sets with Amper fragments.
     */
    context(KMPPBindingPluginPart)
    private val KotlinSourceSet.nearestAmperFragments: List<FragmentWrapper>
        get() = amperFragment?.let { listOf(it) }
            ?: dependsOn.flatMap { it.nearestAmperFragments }

    /**
     * The Amper fragment corresponding to this source set, or the most common one among the Amper fragments associated
     * to the nearest source sets that this source set depends on.
     * We basically go down every path in the source set dependency tree until we find source sets with Amper fragments,
     * and among those, take the fragment that covers the most platforms.
     */
    context(KMPPBindingPluginPart)
    val KotlinSourceSet.mostCommonNearestAmperFragment get() = nearestAmperFragments.let { nearest ->
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
        get() = when {
            !isTest && name == module.rootFragment.name -> "commonMain"
            // TODO Add variants support.
            !isTest && name == "android" -> "androidMain"
            isTest && name == "androidTest" -> "androidUnitTest"
            isTest && name == module.rootTestFragment.name -> "commonTest"
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

    context(KMPEAware)
    val LeafFragmentWrapper.targetCompilation: KotlinCompilation<KotlinCommonOptions>?
        get() = target?.run { (this@targetCompilation as LeafFragment).compilation }

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
