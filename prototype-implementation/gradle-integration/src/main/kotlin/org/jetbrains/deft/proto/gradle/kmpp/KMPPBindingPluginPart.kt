package org.jetbrains.deft.proto.gradle.kmpp

import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.PlatformFamily
import org.jetbrains.deft.proto.gradle.BindingPluginPart
import org.jetbrains.deft.proto.gradle.PluginPartCtx
import org.jetbrains.deft.proto.gradle.addDependency
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
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
        // Initialize targets and add dependencies.
        module.artifactPlatforms.forEach { target ->
            when (target.family) {
                PlatformFamily.ANDROID -> configureAndAddDependenciesForTarget(target, "androidMain") { android { doConfigure() } }
                PlatformFamily.JVM -> configureAndAddDependenciesForTarget(target, "jvmMain") { jvm { doConfigure() } }
                PlatformFamily.IOS -> configureAndAddDependenciesForTarget(target, "iosMain") { ios { doConfigure() } }
                PlatformFamily.JS -> configureAndAddDependenciesForTarget(target, "jsMain") { js { doConfigure() } }
                PlatformFamily.NATIVE -> TODO()
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
        kotlinMPE.applyForKotlinSourceSet(sourceSetName) {
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