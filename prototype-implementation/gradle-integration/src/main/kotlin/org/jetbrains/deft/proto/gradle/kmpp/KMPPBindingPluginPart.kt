package org.jetbrains.deft.proto.gradle.kmpp

import org.jetbrains.deft.proto.frontend.Fragment
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.gradle.BindingPluginPart
import org.jetbrains.deft.proto.gradle.PluginPartCtx
import org.jetbrains.deft.proto.gradle.buildDir
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
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
        initTargets()
        initFragments()
    }

    private fun initTargets() {
        module.artifactPlatforms.forEach { target ->
            when (target) {
                Platform.ANDROID -> kotlinMPE.android { doConfigure() }
                Platform.JVM -> kotlinMPE.jvm { doConfigure() }
                Platform.IOS_ARM_SIMULATOR -> kotlinMPE.ios { doConfigure() }
                Platform.IOS_X_64 -> kotlinMPE.ios { doConfigure() }
                Platform.IOS_ARM_X64 -> kotlinMPE.ios { doConfigure() }
                Platform.JS -> kotlinMPE.js { doConfigure() }
            }
        }
    }

    private fun initFragments() {
        // First iteration - create source sets and add dependencies.
        module.fragments.forEach { fragment ->
            fragment.maybeCreateSourceSet {
                dependencies {
                    fragment.externalDependencies.forEach { externalDependency ->
                        if (externalDependency.startsWith(":")) implementation(project(externalDependency))
                        else implementation(externalDependency)
                    }
                }
            }
        }

        // Second iteration - create dependencies between fragments (aka source sets) and set source/resource directories.
        module.fragments.forEach { fragment ->
            val sourceSet = fragment.sourceSet

            // Set dependencies.
            fragment.fragmentDependencies.forEach {
                sourceSet.dependsOn(it.target.sourceSet)
            }

            // Set sources and resources.
            sourceSet.kotlin.setSrcDirs(listOf(fragment.srcPath.toFile()))
            sourceSet.resources.setSrcDirs(listOf(fragment.resourcesPath.toFile()))
        }
    }

    private fun KotlinAndroidTarget.doConfigure() {
    }

    private fun KotlinJvmTarget.doConfigure() {
    }

    private fun KotlinNativeTarget.doConfigure() {
    }

    private fun KotlinJsTargetDsl.doConfigure() {
    }

    // ------
    private val Fragment.path get() = module.buildDir.resolve(name)
    private val Fragment.srcPath get() = path.resolve("src")
    private val Fragment.resourcesPath get() = path.resolve("resources")
    private val Fragment.sourceSet get() = kotlinMPE.sourceSets.getByName(name)
    private fun Fragment.maybeCreateSourceSet(block: KotlinSourceSet.() -> Unit) {
        val sourceSet = kotlinMPE.sourceSets.maybeCreate(name)
        sourceSet.block()
    }

}