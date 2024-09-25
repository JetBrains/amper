/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.android

import com.android.build.gradle.BaseExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.keyAlias
import org.jetbrains.amper.frontend.schema.keyPassword
import org.jetbrains.amper.frontend.schema.storeFile
import org.jetbrains.amper.frontend.schema.storePassword
import org.jetbrains.amper.gradle.base.AmperNamingConventions
import org.jetbrains.amper.gradle.base.PluginPartCtx
import org.jetbrains.amper.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.amper.gradle.contains
import org.jetbrains.amper.gradle.kmpp.KMPEAware
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention
import org.jetbrains.amper.gradle.kmpp.doDependsOn
import org.jetbrains.amper.gradle.kotlin.configureCompilerOptions
import org.jetbrains.amper.gradle.layout
import org.jetbrains.amper.gradle.moduleDir
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.reader

const val SIGNING_CONFIG_NAME = "sign"

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
                        sourceSet.assets.setSrcDirs(listOf(module.moduleDir.resolve("assets")))
                        sourceSet.res.setSrcDirs(listOf(module.moduleDir.resolve("res")))
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
                compilations.configureEach { compilation ->
                    // TODO do we need this at all? It seems redundant with the settings done in the KMP binding plugin
                    compilation.compileTaskProvider.configureCompilerOptions(fragment.settings)

                    compilation.kotlinSourceSets.forEach { compilationSourceSet ->
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
        firstAndroidFragment.settings.jvm.release?.let { release ->
            androidPE?.compileOptions {
                val compileOptions = it
                compileOptions.targetCompatibility(release.legacyNotation)
                compileOptions.sourceCompatibility(release.legacyNotation)
            }
            project.tasks.withType(JavaCompile::class.java).configureEach {
                it.options.release.set(release.releaseNumber)
            }
        }

        val signing = firstAndroidFragment.settings.android.signing

        if (signing.propertiesFile.exists()) {
            val keystoreProperties = Properties().apply {
                signing.propertiesFile.reader().use { reader ->
                    load(reader)
                }
            }
            androidPE?.apply {
                signingConfigs {
                    it.create(SIGNING_CONFIG_NAME) {
                        keystoreProperties.storeFile?.let { storeFile ->
                            it.storeFile = Path(storeFile).toFile()
                        }
                        keystoreProperties.storePassword?.let { storePassword ->
                            it.storePassword = storePassword
                        }
                        keystoreProperties.keyAlias?.let { keyAlias ->
                            it.keyAlias = keyAlias
                        }
                        keystoreProperties.keyPassword?.let { keyPassword ->
                            it.keyPassword = keyPassword
                        }
                    }
                }
            }
        } else {
            val path = (project.projectDir.toPath() / signing.propertiesFile.pathString)
                .normalize()
                .absolutePathString()

            project.logger.warn("Properties file $path not found. Signing will not be configured")
        }

        leafPlatformFragments.forEach { fragment ->
            val androidSettings = fragment.settings.android
            androidPE?.apply {
                compileSdkVersion(androidSettings.compileSdk.versionNumber)
                defaultConfig.apply {
                    if (!module.type.isLibrary()) applicationId = androidSettings.applicationId
                    namespace = androidSettings.namespace
                    minSdk = androidSettings.minSdk.versionNumber
                    maxSdk = androidSettings.maxSdk?.versionNumber
                    targetSdk = androidSettings.targetSdk.versionNumber
                    versionCode = androidSettings.versionCode
                    versionName = androidSettings.versionName
                }

                buildTypes {
                    it.getByName("release") {
                        it.proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                        it.isDebuggable = false
                        it.isMinifyEnabled = true
                        if (module.type.isApplication()) {
                            it.isShrinkResources = true
                        }
                        signingConfigs.findByName(SIGNING_CONFIG_NAME)?.let { signing ->
                            it.signingConfig = signing
                        }
                    }
                }
            }
        }
    }
}
