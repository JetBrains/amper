package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/**
 * Plugin logic, bind to specific module, when multiple targets are available.
 */
class KMPPModulePlugin(
    ctx: ModulePluginCtx,
) : ModulePlugin by ctx {

    private val kotlinMPE: KotlinMultiplatformExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    fun apply() {
        // Initialize targets and add dependencies.
        targets.forEach { target ->
            when (target) {
                "android" -> configureAndAddDependenciesForTarget(target, "androidMain") { android { doConfigure() } }
                "jvm" -> configureAndAddDependenciesForTarget(target, "jvmMain") { jvm { doConfigure() } }
                "ios" -> configureAndAddDependenciesForTarget(target, "iosMain") { ios { doConfigure() } }
                "js" -> configureAndAddDependenciesForTarget(target, "jsMain") { js { doConfigure() } }
                Model.defaultTarget -> configureAndAddDependenciesForTarget(target, "commonMain") { }
            }
        }
    }

    /**
     * Perform specific configurations for module targets and add dependencies.
     */
    private fun configureAndAddDependenciesForTarget(
        target: String,
        sourceSetName: String,
        targetSpecific: KotlinMultiplatformExtension.() -> Unit
    ) {
        kotlinMPE.targetSpecific()
        kotlinMPE.applyForSourceSet(sourceSetName) {
            dependencies {
                model.getDeclaredDependencies(moduleId, target).forEach { dependency ->
                    addDependency(moduleIdToPath, dependency)
                }
            }
        }
    }

    private fun KotlinAndroidTarget.doConfigure() {

    }

    private fun KotlinJvmTarget.doConfigure() {
        allCollapsed["target.jvm.toolchain"]?.first()?.toInt()?.let { kotlinMPE.jvmToolchain(it) }
    }

    private fun KotlinNativeTarget.doConfigure() {

    }

    private fun KotlinJsTargetDsl.doConfigure() {

    }

}