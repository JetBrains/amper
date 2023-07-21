package org.jetbrains.deft.proto.gradle.android

import com.android.build.gradle.BaseExtension
import org.jetbrains.deft.proto.frontend.AndroidPart
import org.jetbrains.deft.proto.frontend.ComposePart
import org.jetbrains.deft.proto.frontend.JavaPart
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention
import org.jetbrains.deft.proto.gradle.kmpp.doDependsOn
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

fun applyAndroidAttributes(ctx: PluginPartCtx) = AndroidBindingPluginPart(ctx).apply()

@Suppress("LeakingThis")
open class AndroidAwarePart(
    ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, Platform.ANDROID), DeftNamingConventions {

    internal val androidPE = project.extensions.findByName("android") as BaseExtension?

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

    /**
     * Entry point for this plugin part.
     */
    fun apply() {
        adjustCompilations()
        adjustAndroidSourceSets()
        applySettings()
    }

    private fun adjustAndroidSourceSets() = with(AndroidDeftNamingConvention) {
        // Clear android source sets that are not created by us.
        // Adjust that source sets whose matching kotlin source sets are created by us.
        // Can be called after project evaluation.
        androidSourceSets?.all {
            val fragment = it.deftFragment
            if (fragment != null) {
                it.kotlin.setSrcDirs(fragment.sourcePaths)
                it.java.setSrcDirs(fragment.sourcePaths)
                it.resources.setSrcDirs(fragment.resourcePaths)
                it.res.setSrcDirs(fragment.androidResPaths)
                it.manifest.srcFile("${fragment.sourcePath}/Manifest.xml")
            } else {
                it.kotlin.setSrcDirs(emptyList<Any>())
                it.java.setSrcDirs(emptyList<Any>())
                it.resources.setSrcDirs(emptyList<Any>())
            }
        }
    }

    private fun adjustCompilations() = with(KotlinDeftNamingConvention) {
        leafPlatformFragments.forEach { fragment ->
            project.afterEvaluate {
                val androidTarget = fragment.target ?: return@afterEvaluate
                val compilations = if (fragment.isTest) {
                    androidTarget.compilations.matching { it.name.lowercase().contains("test") }
                } else {
                    androidTarget.compilations.matching { !it.name.lowercase().contains("test") }
                }
                compilations.configureEach {
                    fragment.parts.find<JavaPart>()?.let { part ->
                        it.compileTaskProvider.configure {
                            part.target?.let { target ->
                                it as KotlinCompilationTask<KotlinJvmCompilerOptions>
                                println("Compilation ${it.name} has jvmTarget $target")
                                it.compilerOptions.jvmTarget.set(JvmTarget.fromTarget(target))
                            }
                        }
                    }

                    it.kotlinSourceSets.forEach { compilationSourceSet ->
                        if (compilationSourceSet != fragment.kotlinSourceSet) {
                            println("Attaching fragment ${fragment.name} to compilation ${it.name}")
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
            firstAndroidFragment.parts.find<JavaPart>()?.source?.let {
                compileOptions.setSourceCompatibility(it)
            }
            firstAndroidFragment.parts.find<JavaPart>()?.target?.let {
                compileOptions.setTargetCompatibility(it)
            }
        }
        androidPE?.namespace = "com.example.sampleproject"
        if (module.leafNonTestFragments.any { it.parts.find<ComposePart>()?.enabled == true }) {
            @Suppress("UnstableApiUsage")
            androidPE?.buildFeatures?.compose = true
        }
        leafPlatformFragments.forEach { fragment ->
            val part = fragment.parts.find<AndroidPart>() ?: return@forEach
            androidPE?.apply {
                part.compileSdkVersion?.let { compileSdkVersion(it) }
                defaultConfig.apply {
                    part.applicationId?.let { applicationId = it }
                    part.namespace?.let { namespace = it }
                    part.minSdk?.let { minSdkVersion(it) }
                    part.minSdkPreview?.let { minSdkPreview = it }
                    part.maxSdk?.let { maxSdkVersion(it) }
                    part.targetSdk?.let { targetSdkVersion(it) }
                }
            }
        }
    }
}