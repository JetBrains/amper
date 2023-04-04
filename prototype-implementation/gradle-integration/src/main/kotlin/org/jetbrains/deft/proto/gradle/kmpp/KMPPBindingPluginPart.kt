package org.jetbrains.deft.proto.gradle.kmpp

import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.BindingPluginPart
import org.jetbrains.deft.proto.gradle.FragmentWrapper
import org.jetbrains.deft.proto.gradle.PluginPartCtx
import org.jetbrains.deft.proto.gradle.buildDir
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.io.File
import java.util.*

fun applyKotlinMPAttributes(ctx: PluginPartCtx) = KMPPBindingPluginPart(ctx).apply()

/**
 * Plugin logic, bind to specific module, when multiple targets are available.
 */
class KMPPBindingPluginPart(
    ctx: PluginPartCtx,
) : BindingPluginPart by ctx {

    private val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    fun apply() {
        initTargets()
        initFragments()
    }

    private fun initTargets() {
        module.artifacts.forEach { artifact ->
            artifact.platforms.forEach { platform ->
                check(platform.isLeaf) { "Artifacts can't contain non leaf targets. Non leaf target: $platform" }
                val targetName = platform.name.lowercase(Locale.getDefault())
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
            applyOtherFragmentsPartsRecursively(it)
            System.err.println("DEPEND FROM $name ON ${it.name}")
            dependsOn(wrapper.sourceSet)
        }

        // Clear sources and resources for existing source sets.
        val existingSourceSets = kotlinMPE.sourceSets.toList()
        existingSourceSets.forEach {
            it.kotlin.setSrcDirs(emptyList<File>())
            it.resources.setSrcDirs(emptyList<File>())
        }

        // First iteration - create source sets and add dependencies.
        module.fragments.forEach { fragment ->
            fragment.maybeCreateSourceSet {
                dependencies {
                    fragment.externalDependencies.forEach { externalDependency ->
                        when (externalDependency) {
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
            sourceSet.applyOtherFragmentsPartsRecursively(fragment)

            // Set dependencies.
            fragment.fragmentDependencies.forEach {
                sourceSet.doDependsOn(it.target)
            }

            // Set sources and resources.
            sourceSet.kotlin.srcDirs.clear()
            sourceSet.kotlin.srcDir(fragment.part<KotlinFragmentPart>()?.srcFolderName ?: fragment.srcPath)
            sourceSet.resources.srcDirs.clear()
            sourceSet.resources.srcDir("${fragment.part<KotlinFragmentPart>()?.srcFolderName ?: fragment.srcPath}/resources")
        }

        // Third iteration - adjust kotlin prebuilt source sets to match created ones.
        println("EXISING ARTIFACTS: ${module.artifacts.joinToString { it.name }}")
        module.artifacts.forEach { artifact ->
            println("ADJUSTING EXISING ARTIFACT: $artifact")
            artifact.platforms.forEach inner@{ platform ->
                val targetName = platform.name.lowercase(Locale.getDefault())
                val target = kotlinMPE.targets.findByName(targetName) ?: return@inner

                val compilation = (if (artifact.name.contains("Test"))
                    target.compilations.findByName("test")
                else target.compilations.findByName("main")) ?: return@inner
                println("ADJUSTING EXISING: $compilation")
                compilation.defaultSourceSet.apply {
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

    private fun KotlinSourceSet.applyOtherFragmentsPartsRecursively(
        from: Fragment
    ): LanguageSettingsBuilder = languageSettings.apply {
        val wrapper = from as? FragmentWrapper ?: FragmentWrapper(from)
        doApplyPart(wrapper.part<KotlinFragmentPart>())
        from.fragmentDependencies.forEach {
            applyOtherFragmentsPartsRecursively(it.target)
        }
    }

    private fun KotlinSourceSet.doApplyPart(kotlinPart: KotlinFragmentPart?) = languageSettings.apply {
        // TODO Propagate properly.
        kotlinPart ?: return@apply
        // TODO Change defaults to some merge chain. Now languageVersion checking ruins build.
        languageVersion = kotlinPart.languageVersion ?: "1.8"
        apiVersion = kotlinPart.apiVersion ?: "1.8"
        if (progressiveMode != (kotlinPart.progressiveMode ?: false)) progressiveMode =
            kotlinPart.progressiveMode ?: false
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