package org.jetbrains.deft.proto.gradle.kmpp

import com.android.build.gradle.internal.tasks.manifest.mergeManifests
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

fun applyKotlinMPAttributes(ctx: PluginPartCtx) = KMPPBindingPluginPart(ctx).apply()

/**
 * Plugin logic, bind to specific module, when multiple targets are available.
 */
class KMPPBindingPluginPart(
    ctx: PluginPartCtx,
) : BindingPluginPart by ctx {

    private val kotlinMPE: KotlinMultiplatformExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    fun apply() {
        initTargets()
        initFragments()
    }

    private fun initTargets() {
        module.artifacts.forEach { artifact ->
            artifact.platforms.forEach { platform ->
                check(platform.isLeaf) { "Artifacts can't contain non leaf targets. Non leaf target: $platform" }
                val targetName = "${artifact.name}${platform.name}"
                when (platform) {
                    Platform.ANDROID -> kotlinMPE.android(targetName) { doConfigure() }
                    Platform.JVM -> kotlinMPE.jvm(targetName) { doConfigure() }
                    Platform.IOS_ARM64 -> kotlinMPE.iosArm64(targetName) { doConfigure() }
                    Platform.IOS_SIMULATOR_ARM64 -> kotlinMPE.iosSimulatorArm64(targetName) { doConfigure() }
                    Platform.IOS_X64 -> kotlinMPE.iosX64(targetName) { doConfigure() }
                    Platform.JS -> kotlinMPE.js { doConfigure() }
                    else -> error("Unsupported platform: $platform")
                }
            }
        }
    }

    private fun initFragments() {
        // Introduced function to remember to propagate language settings.
        fun KotlinSourceSet.doDependsOn(it: Fragment) {
            val wrapper = it as? FragmentWrapper ?: FragmentWrapper(it)
            applyPart(wrapper.part<KotlinFragmentPart>())
            dependsOn(wrapper.sourceSet)
        }

        // First iteration - create source sets and add dependencies.
        module.fragments.forEach { fragment ->
            fragment.maybeCreateSourceSet {
                dependencies {
                    fragment.externalDependencies.forEach { externalDependency ->
                        when(externalDependency) {
                            is MavenDependency -> implementation(externalDependency.coordinates)
                            is PotatoModuleDependency -> with(externalDependency) {
                                implementation(model.module.linkedProject)
                            }
                            else -> error("Unsupported dependency type: $externalDependency")
                        }
                    }
                }
            }
        }

        // Second iteration - create dependencies between fragments (aka source sets) and set source/resource directories.
        module.fragments.forEach { fragment ->
            val sourceSet = fragment.sourceSet

            // Apply language settings.
            val part = fragment.part<KotlinFragmentPart>()
            sourceSet.applyPart(part)

            // Set dependencies.
            fragment.fragmentDependencies.forEach {
                sourceSet.doDependsOn(it.target)
            }

            // Set sources and resources.
            sourceSet.kotlin.setSrcDirs(listOf(fragment.srcPath.toFile()))
            sourceSet.resources.setSrcDirs(listOf(fragment.resourcesPath.toFile()))
        }

        // Third iteration - adjust kotlin prebuilt source sets to match created ones.
        module.artifacts.forEach { artifact ->
            artifact.platforms.forEach inner@ { platform ->
                val targetName = "${artifact.name}${platform.name}"
                val target = kotlinMPE.targets.findByName(targetName) ?: return@inner
                val mainCompilation = target.compilations.findByName("main") ?: return@inner
                mainCompilation.defaultSourceSet.apply {
                    artifact.fragments.forEach {
                        doDependsOn(it)
                    }
                }
            }
        }

        module.fragments.forEach { fragment ->
            val possiblePrebuiltName = "${fragment.name}Main"
            findSourceSet(possiblePrebuiltName)?.let {
                it.doDependsOn(fragment)
            }
        }

    }

    private fun KotlinSourceSet.applyPart(kotlinPart: KotlinFragmentPart?) = languageSettings.apply {
        // TODO Propagate properly.
        kotlinPart ?: return@apply
        if (languageVersion == null) languageVersion = kotlinPart.languageVersion
        if (apiVersion == null) apiVersion = kotlinPart.apiVersion
        if (progressiveMode != (kotlinPart.progressiveMode ?: false)) progressiveMode = kotlinPart.progressiveMode ?: false
        kotlinPart.languageFeatures.forEach { enableLanguageFeature(it) }
        kotlinPart.optIns.forEach { optIn(it) }
    }

    private fun KotlinAndroidTarget.doConfigure() {

    }

    private fun KotlinJvmTarget.doConfigure() {
    }

    private fun KotlinNativeTarget.doConfigure() {
    }

    private fun KotlinNativeTargetWithSimulatorTests.doConfigure() {
    }

    private fun KotlinJsTargetDsl.doConfigure() {
    }

    // ------
    private val Fragment.path get() = module.buildDir.resolve(name)
    private val Fragment.srcPath get() = path.resolve("src")
    private val Fragment.resourcesPath get() = path.resolve("resources")
    private fun findSourceSet(name: String) = kotlinMPE.sourceSets.findByName(name)
    private val Fragment.sourceSet get() = kotlinMPE.sourceSets.getByName(name)
    private fun Fragment.maybeCreateSourceSet(block: KotlinSourceSet.() -> Unit) {
        val sourceSet = kotlinMPE.sourceSets.maybeCreate(name)
        sourceSet.block()
    }

}