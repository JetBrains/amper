package org.jetbrains.deft.proto.gradle.android

import com.android.build.gradle.BaseExtension
import org.jetbrains.deft.proto.frontend.AndroidPart
import org.jetbrains.deft.proto.frontend.JavaPart
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.ProductType
import org.jetbrains.deft.proto.gradle.LayoutMode
import org.jetbrains.deft.proto.gradle.android.AndroidDeftNamingConvention.deftFragment
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.contains
import org.jetbrains.deft.proto.gradle.deftLayout
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention
import org.jetbrains.deft.proto.gradle.kmpp.doDependsOn
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

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
        // Adjust android source sets in case of gradle script absence.
        // We need to do this here, since AGP tries to access manifest before
        // `afterProject` evaluation.
        if (deftLayout == LayoutMode.DEFT || deftLayout == LayoutMode.COMBINED)
            adjustAndroidSourceSetsDeftSpecific()
    }

    override fun applyAfterEvaluate() {
        if (deftLayout == LayoutMode.DEFT || deftLayout == LayoutMode.COMBINED) {
            clearNonManagerSourceSetDirs()
            adjustAndroidSourceSetsDeftSpecific()
        }
    }

    override fun onDefExtensionChanged() {
        // Called only when extension in script is toggled, so
        // [adjustAndroidSourceSetsDeftSpecific] invocation in [applyBeforeEvaluate]
        // was not triggered.
        if (deftLayout == LayoutMode.DEFT || deftLayout == LayoutMode.COMBINED)
            adjustAndroidSourceSetsDeftSpecific()
    }

    private fun clearNonManagerSourceSetDirs() {
        // Clear android source sets that are not created by us.
        // Can be evaluated after project evaluation.
        androidSourceSets?.all {
            it.deftFragment ?: return@all
            it.kotlin.setSrcDirs(emptyList<Any>())
            it.java.setSrcDirs(emptyList<Any>())
            it.resources.setSrcDirs(emptyList<Any>())
        }
    }

    private var sourceSetsAdjusted = false

    private fun adjustAndroidSourceSetsDeftSpecific() = with(AndroidDeftNamingConvention) {
        if (!sourceSetsAdjusted) {
            sourceSetsAdjusted = true
            // Adjust that source sets whose matching kotlin source sets are created by us.
            // Can be evaluated after project evaluation.
            androidSourceSets?.all {
                val fragment = it.deftFragment ?: return@all
                it.kotlin.setSrcDirs(fragment.sourcePaths)
                it.java.setSrcDirs(fragment.sourcePaths)
                it.resources.setSrcDirs(fragment.resourcePaths)
                it.res.setSrcDirs(fragment.androidResPaths)
                it.manifest.srcFile("${fragment.sourcePath}/Manifest.xml")
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

                    // It is related with JetGradle plugin, which uses only `declaredSourceSets` for import
                    // TODO (Anton Prokhorov): investigate
                    it.source(fragment.kotlinSourceSet ?: error("Can not find a sourceSet for fragment: ${fragment.name}"))
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