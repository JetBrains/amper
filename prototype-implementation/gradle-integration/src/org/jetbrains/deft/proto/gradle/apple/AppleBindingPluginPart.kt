package org.jetbrains.deft.proto.gradle.apple

import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.ProductType
import org.jetbrains.deft.proto.gradle.base.BindingPluginPart
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.kmpp.KMPEAware
import org.jetbrains.gradle.apple.AppleProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.extraProperties


val iosPlatforms = setOf(Platform.IOS_ARM64, Platform.IOS, Platform.IOS_SIMULATOR_ARM64, Platform.IOS_X64)

class AppleBindingPluginPart(ctx: PluginPartCtx) : KMPEAware, BindingPluginPart by ctx {
    override val kotlinMPE: KotlinMultiplatformExtension
        get() = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    private val applePE: AppleProjectExtension? get() = project.extensions.findByName("apple") as AppleProjectExtension?

    override val needToApply by lazy { module.type == ProductType.IOS_APP }

    override fun applyBeforeEvaluate() {
        project.extraProperties.set("generateBuildableXcodeproj.skipKotlinFrameworkDependencies", "true")

        // Apply plugin
        project.plugins.apply("org.jetbrains.gradle.apple.applePlugin")

        // Add ios App
        applePE?.iosApp {
            productInfo["UILaunchScreen"] = mapOf<String, Any>()
        }

        // Get main and test apple sourcesets added by apple plugins
        val appleMain = applePE?.sourceSets?.findByName("iosAppMain") ?: error("It is required to have iosAppMain sourceSet")
        val appleTest = applePE?.sourceSets?.findByName("iosAppTest") ?: error("It is required to have iosAppTest sourceSet")

        module.fragments.forEach {
            if (it.platforms.all { iosPlatforms.contains(it) }) {
                if (it.isTest) {
                    appleTest.apple.srcDirs(it.src)
                } else {
                    appleMain.apple.srcDirs(it.src)
                }
            }
        }
    }
}