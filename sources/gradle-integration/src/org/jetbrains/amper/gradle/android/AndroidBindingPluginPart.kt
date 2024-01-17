/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.android

import com.android.build.gradle.BaseExtension
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.ProductType
import org.jetbrains.amper.gradle.base.AmperNamingConventions
import org.jetbrains.amper.gradle.base.PluginPartCtx
import org.jetbrains.amper.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.amper.gradle.buildDir
import org.jetbrains.amper.gradle.contains
import org.jetbrains.amper.gradle.kmpp.KMPEAware
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention
import org.jetbrains.amper.gradle.kmpp.doDependsOn
import org.jetbrains.amper.gradle.layout
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.io.File

@Suppress("LeakingThis")
open class AndroidAwarePart(
    ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, Platform.ANDROID), AmperNamingConventions {

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

    private fun adjustAndroidSourceSets() = with(AndroidAmperNamingConvention) {
        val shouldAddAndroidRes = module.artifactPlatforms.size == 1 &&
                module.artifactPlatforms.contains(Platform.ANDROID)

        // Adjust that source sets whose matching kotlin source sets are created by us.
        // Can be evaluated after project evaluation.
        androidSourceSets?.all { sourceSet ->
            val fragment = sourceSet.amperFragment
            when {
                // Do AMPER specific.
                layout == Layout.AMPER && fragment != null -> {
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
                    while (queue.isNotEmpty()) {
                        val next = queue.removeFirst()
                        queue.addAll(next.refineDependencies)
                        if (next.androidSourceSet == null) {
                            collectedResources.add(next.resourcesPath.toFile())
                        }
                    }
                    collectedResources.add(fragment.resourcesPath.toFile())
                    sourceSet.resources.setSrcDirs(collectedResources)
                }

                layout == Layout.AMPER && fragment == null -> {
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

    private fun adjustCompilations() = with(KotlinAmperNamingConvention) {
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
                    fragment.settings.jvm.let { jvmSettings ->
                        it.compileTaskProvider.configure {
                            jvmSettings?.target.let { target ->
                                it as KotlinCompilationTask<KotlinJvmCompilerOptions>
                                it.compilerOptions.jvmTarget.set(target?.schemaValue?.let {
                                    JvmTarget.fromTarget(it)
                                })
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
            val jvmTarget = firstAndroidFragment.settings.jvm?.target
            val javaSource = firstAndroidFragment.settings.java?.source ?: jvmTarget
            jvmTarget?.schemaValue?.let { compileOptions.setTargetCompatibility(it) }
            javaSource?.schemaValue?.let { compileOptions.setSourceCompatibility(it) }
        }
        leafPlatformFragments.forEach { fragment ->
            val androidSettings = fragment.settings.android
            androidPE?.apply {
                compileSdkVersion(androidSettings.compileSdk.versionNumber)
                defaultConfig.apply {
                    if (!module.type.isLibrary()) androidSettings.applicationId.let { applicationId = it }
                    androidSettings.namespace.let { namespace = it }
                    minSdkVersion(androidSettings.minSdk.versionNumber)
                    maxSdkVersion(androidSettings.maxSdk.versionNumber)
                    targetSdkVersion(androidSettings.targetSdk.versionNumber)
                }
            }
        }
    }
}