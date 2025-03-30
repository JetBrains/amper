/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.java

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.gradle.EntryPointType
import org.jetbrains.amper.gradle.base.AmperNamingConventions
import org.jetbrains.amper.gradle.base.PluginPartCtx
import org.jetbrains.amper.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.amper.gradle.closureSources
import org.jetbrains.amper.gradle.contains
import org.jetbrains.amper.gradle.findEntryPoint
import org.jetbrains.amper.gradle.kmpp.KMPEAware
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.kotlinSourceSet
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.target
import org.jetbrains.amper.gradle.kotlin.configureCompilerOptions
import org.jetbrains.amper.gradle.layout
import org.jetbrains.amper.gradle.replacePenultimatePaths
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.io.path.relativeTo

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class JavaBindingPluginPart(
    ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, Platform.JVM), KMPEAware, AmperNamingConventions {

    companion object {
        val logger: Logger = LoggerFactory.getLogger("some-logger")
    }

    override val kotlinMPE: KotlinMultiplatformExtension =
        project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    override val needToApply by lazy { Platform.JVM in module }

    override fun applyBeforeEvaluate() {
        applyJavaTargetForKotlin()

        if (Platform.ANDROID in module) {
            logger.warn(
                "Cant enable java integration when android is enabled. " +
                        "Module: ${module.userReadableName}"
            )
            return
        }

        adjustJavaGeneralProperties()
    }

    override fun applyAfterEvaluate() {
        adjustSourceDirs()
    }

    private fun applyJavaTargetForKotlin() = with(KotlinAmperNamingConvention) {
        leafPlatformFragments.forEach { fragment ->
            // TODO do we need this at all? It seems redundant with the settings done in the KMP binding plugin
            fragment.targetCompilation?.compileTaskProvider?.configureCompilerOptions(fragment.settings)
        }
    }

    private fun adjustJavaGeneralProperties() {
        if (leafPlatformFragments.size > 1)
        // TODO Add check that all parts values are the same instead of this approach.
            logger.info(
                "Cant apply multiple settings for application plugin. " +
                        "Affected artifacts: ${platformArtifacts.joinToString { it.name }}. " +
                        "Applying application settings from first one."
            )
        val fragment = leafPlatformFragments.firstOrNull() ?: return
        if (module.type.isApplication() && !fragment.settings.compose.enabled) {
            // A 'run' task is added when applying the Application plugin, so it's kinda expected by Gradle users
            // when they have jvm/app modules.
            project.tasks.register("run") {
                it.dependsOn(project.tasks.named("runJvm"))
                it.group = "application"
                it.description = "Run the JVM application."
            }
            // A 'test' task is added when applying the Java or Application plugin, so it's kinda expected by Gradle
            // users when they have jvm/app modules.
            project.tasks.register("test") {
                it.dependsOn(project.tasks.named("jvmTest"))
                it.group = "verification"
                it.description = "Run all JVM tests."
            }
        }

        val jvmSettings = fragment.settings.jvm
        jvmSettings.release?.let { release ->
            project.tasks.withType(JavaCompile::class.java).configureEach {
                it.options.release.set(release.releaseNumber)
            }
        }

        // Do when layout is known.
        project.afterEvaluate {
            if (module.type.isLibrary()) return@afterEvaluate
            val foundMainClass = if (jvmSettings.mainClass != null) {
                jvmSettings.mainClass
            } else {
                val sources = fragment.kotlinSourceSet?.closureSources?.ifEmpty {
                    val kotlinSourceSet = fragment.kotlinSourceSet
                    (kotlinSourceSet as DefaultKotlinSourceSet).compilations.flatMap {
                        it.defaultSourceSet.kotlin.srcDirs
                    }.map { it.toPath() }
                } ?: emptyList()

                findEntryPoint(sources, EntryPointType.JVM, logger)
            }

            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            (fragment.target as KotlinJvmTarget).binaries {
                executable {
                    if (mainClass.orNull == null) {
                        mainClass.set(foundMainClass)
                    }
                }
            }

            // TODO Handle Amper variants gere, when we will switch to manual java source sets creation.
            project.tasks.withType(Jar::class.java) {
                it.manifest {
                    it.attributes["Main-Class"] = foundMainClass
                }
            }
        }
    }

    /**
     * Configures the source directories on the Java source sets based on the chosen layout.
     * This is necessary to pick up Java sources from the correct source directories.
     */
    private fun adjustSourceDirs() {
        when (layout) {
            Layout.AMPER -> setJavaSourceRootsForAmperLayout()
            Layout.GRADLE_JVM -> setJavaSourceRootsForGradleJvmLayout()
            Layout.GRADLE -> Unit // keep things as-is
        }
    }

    private fun setJavaSourceRootsForAmperLayout() {
        val jvmOnlyFragments = module.fragments.filter { it.platforms == setOf(Platform.JVM) }
        val (jvmTestFragments, jvmMainFragments) = jvmOnlyFragments.partition { it.isTest }
        project.javaMainSourceSet?.apply {
            java.setSrcDirs(jvmMainFragments.map { it.src })
            resources.setSrcDirs(emptyList<File>())
        }
        project.javaTestSourceSet?.apply {
            java.setSrcDirs(jvmTestFragments.map { it.src })
            resources.setSrcDirs(emptyList<File>())
        }
    }

    private fun setJavaSourceRootsForGradleJvmLayout() {
        // In gradle-jvm layout, src/{main,test}/java should be mapped to the jvmMain/jvmTest Java source sets,
        // and there is no other directory to map, so commonMain/commonTest should get no java sources, even if there
        // is just the JVM platform.
        project.javaMainSourceSet?.apply { replacePenultimatePaths(java, resources, "main") }
        project.javaTestSourceSet?.apply { replacePenultimatePaths(java, resources, "test") }
    }
}
