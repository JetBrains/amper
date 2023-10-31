/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.android

import com.android.build.gradle.BaseExtension
import org.jetbrains.amper.frontend.*
import org.jetbrains.amper.gradle.*
import org.jetbrains.amper.gradle.base.DeftNamingConventions
import org.jetbrains.amper.gradle.base.PluginPartCtx
import org.jetbrains.amper.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.amper.gradle.kmpp.KMPEAware
import org.jetbrains.amper.gradle.kmpp.KotlinDeftNamingConvention
import org.jetbrains.amper.gradle.kmpp.doDependsOn
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.io.File

@Suppress("LeakingThis")
open class AndroidAwarePart(
    ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, Platform.ANDROID), DeftNamingConventions {

    // Use `get()` property notation, since extension is undefined before [applyBeforeEvaluate] call.
    internal val androidPE get() = project.extensions.findByName("android") as BaseExtension?

    // Use `get()` property notation, since extension is undefined before [applyBeforeEvaluate] call.
    internal val androidSourceSets get() = androidPE?.sourceSets

}

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class AndroidBindingPluginPart(
    ctx: PluginPartCtx,
) : AndroidAwarePart(ctx), KMPEAware {

    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    override val needToApply by lazy { Platform.ANDROID in module }

    /**
     * Entry point for this plugin part.
     */
    override fun applyBeforeEvaluate() {
        when (module.type) {
            ProductType.LIB -> project.plugins.apply("com.android.library")
            else -> project.plugins.apply("com.android.application")
        }

        adjustCompilations()
        applySettings()
        adjustAndroidSourceSets()
    }

    override fun applyAfterEvaluate() {
        // Be sure our adjustments are made lastly.
        project.afterEvaluate {
            adjustAndroidSourceSets()
        }
    }

    private fun adjustAndroidSourceSets() = with(AndroidDeftNamingConvention) {
        val shouldAddAndroidRes = module.artifactPlatforms.size == 1 &&
                module.artifactPlatforms.contains(Platform.ANDROID)

        // Adjust that source sets whose matching kotlin source sets are created by us.
        // Can be evaluated after project evaluation.
        androidSourceSets?.all { sourceSet ->
            val fragment = sourceSet.deftFragment
            when {
                // Do DEFT specific.
                layout == Layout.DEFT && fragment != null -> {
                    sourceSet.kotlin.setSrcDirs(listOf(fragment.src))
                    sourceSet.java.setSrcDirs(listOf(fragment.src))
                    sourceSet.manifest.srcFile(fragment.src.resolve("AndroidManifest.xml"))

                    if (!fragment.isTest && shouldAddAndroidRes) {
                        sourceSet.assets.setSrcDirs(listOf(module.buildDir.resolve("assets")))
                        sourceSet.res.setSrcDirs(listOf(module.buildDir.resolve("res")))
                    }

                    // Also add all resources from dependants.
                    val collectedResources = mutableSetOf<File>()
                    val queue = mutableListOf(fragment)
                    while(queue.isNotEmpty()) {
                        val next = queue.removeFirst()
                        queue.addAll(next.refineDependencies)
                        if (next.androidSourceSet == null) {
                            collectedResources.add(next.resourcesPath.toFile())
                        }
                    }
                    collectedResources.add(fragment.resourcesPath.toFile())
                    sourceSet.resources.setSrcDirs(collectedResources)
                }

                layout == Layout.DEFT && fragment == null -> {
                    listOf(
                        sourceSet.kotlin,
                        sourceSet.java,
                        sourceSet.resources,
                        sourceSet.assets,
                        sourceSet.res,
                    ).forEach { it.setSrcDirs(emptyList<File>()) }
                }
            }
        }
    }

    private fun adjustCompilations() = with(KotlinDeftNamingConvention) {
        leafPlatformFragments.forEach { fragment ->
            project.afterEvaluate {
                val androidTarget = fragment.target ?: return@afterEvaluate
                val compilations = if (fragment.isTest) {
                    androidTarget.compilations.matching {
                        val lowercaseName = it.name.lowercase()
                        // Collect only unit test, but ignore instrumented test, since they will be
                        // supported by supplementary modules.
                        lowercaseName.contains("test") && lowercaseName.contains("unit")
                    }
                } else {
                    androidTarget.compilations.matching { !it.name.lowercase().contains("test") }
                }
                compilations.configureEach {
                    fragment.parts.find<JvmPart>()?.let { part ->
                        it.compileTaskProvider.configure {
                            part.target?.let { target ->
                                it as KotlinCompilationTask<KotlinJvmCompilerOptions>
                                it.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(target))
                            }
                        }
                    }

                    it.kotlinSourceSets.forEach { compilationSourceSet ->
                        if (compilationSourceSet != fragment.kotlinSourceSet) {
                            compilationSourceSet.doDependsOn(fragment)
                        }
                    }
                }
            }
        }
    }

    private fun applySettings() {
        val firstAndroidFragment = leafPlatformFragments.first()
        androidPE?.compileOptions {
            val compileOptions = it
            val jvmTarget = firstAndroidFragment.parts.find<JvmPart>()?.target
            val javaSource = firstAndroidFragment.parts.find<JavaPart>()?.source ?: jvmTarget
            jvmTarget?.let {
                compileOptions.setTargetCompatibility(it)
            }
            javaSource?.let {
                compileOptions.setSourceCompatibility(it)
            }
        }
        leafPlatformFragments.forEach { fragment ->
            val part = fragment.parts.find<AndroidPart>() ?: return@forEach
            androidPE?.apply {
                part.compileSdk?.let { compileSdkVersion(it) }
                defaultConfig.apply {
                    if (!module.type.isLibrary()) part.applicationId?.let { applicationId = it }
                    part.namespace?.let { namespace = it }
                    part.minSdk?.let { minSdkVersion(it) }
                    part.maxSdk?.let { maxSdkVersion(it) }
                    part.targetSdk?.let { targetSdkVersion(it) }
                }
            }
        }
    }
}