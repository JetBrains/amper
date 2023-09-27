package org.jetbrains.deft.proto.gradle.android

import com.android.build.gradle.BaseExtension
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.android.AndroidDeftNamingConvention.deftFragment
import org.jetbrains.deft.proto.gradle.base.DeftNamingConventions
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.deft.proto.gradle.contains
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.deft.proto.gradle.kmpp.KotlinDeftNamingConvention
import org.jetbrains.deft.proto.gradle.kmpp.doDependsOn
import org.jetbrains.deft.proto.gradle.layoutMode
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
        clearNonManagerSourceSetDirs()
        adjustAndroidSourceSets()
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

    private fun adjustAndroidSourceSets() = with(AndroidDeftNamingConvention) {
        // Adjust that source sets whose matching kotlin source sets are created by us.
        // Can be evaluated after project evaluation.
        with(layoutMode) {
            androidSourceSets?.all {
                val fragment = it.deftFragment ?: return@all

                val sources = fragment.modifyManagedSources(it.name, null)
                if (sources != null) {
                    it.kotlin.setSrcDirs(sources)
                    it.java.setSrcDirs(sources)
                    // FIXME Replace by more complicated layout mode (I guess).
                    it.manifest.srcFile(sources.first().resolve("AndroidManifest.xml"))
                }

                val resources = fragment.modifyManagedResources(it.name, null)
                if (resources != null) {
                    it.resources.setSrcDirs(resources)
                    it.res.setSrcDirs(resources)
                }
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
                    fragment.parts.find<JvmPart>()?.let { part ->
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