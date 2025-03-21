/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.java

import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.util.GradleVersion
import org.jetbrains.amper.frontend.Layout
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.gradle.EntryPointType
import org.jetbrains.amper.gradle.base.AmperNamingConventions
import org.jetbrains.amper.gradle.base.PluginPartCtx
import org.jetbrains.amper.gradle.base.SpecificPlatformPluginPart
import org.jetbrains.amper.gradle.closureSources
import org.jetbrains.amper.gradle.contains
import org.jetbrains.amper.gradle.findEntryPoint
import org.jetbrains.amper.gradle.java.JavaAmperNamingConvention.maybeCreateJavaSourceSet
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

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class JavaBindingPluginPart(
    ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, Platform.JVM), KMPEAware, AmperNamingConventions {

    companion object {
        val logger: Logger = LoggerFactory.getLogger("some-logger")
    }

    private val javaAPE: JavaApplication? get() = project.extensions.findByType(JavaApplication::class.java)
    internal val javaPE: JavaPluginExtension get() = project.extensions.getByType(JavaPluginExtension::class.java)

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
        addJavaIntegration()
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
            // We could use the else branch also for Gradle < 8.7, but the current fallback implementation is not ideal
            // in terms of interop, so for now we keep it like this.
            // For example, the gradle-interop example project uses the sqldelight Gradle plugin, which forces the
            // KGP version to 1.8.22, making it impossible to customize the main class.
            if (GradleVersion.current() < GradleVersion.version("8.7")) {
                project.plugins.apply(ApplicationPlugin::class.java)
            } else {
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

            if (GradleVersion.current() < GradleVersion.version("8.7")) {
                javaAPE?.apply {
                    // Check if main class is set in the build script.
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


    // TODO Rewrite this completely by not calling
    //  KMPP code and following out own conventions.
    private fun addJavaIntegration() {
        kotlinMPE.targets.toList().forEach {
            // Apparently this is still necessary even in Gradle 8.7+, otherwise the app doesn't see java classes at
            // runtime (at least in gradle-jvm layout, probably because we use custom src dirs).
            @Suppress("DEPRECATION")
            if (it is KotlinJvmTarget) it.withJava()
        }

        // Set sources for all Amper related source sets.
        platformFragments.forEach {
            it.maybeCreateJavaSourceSet()
        }
    }

    private fun adjustSourceDirs() {
        val onlyJavaFragments = module.fragments.filter { it.platforms.firstOrNull() == Platform.JVM }
        javaPE.sourceSets.findByName("main")?.apply {
            when (layout) {
                Layout.GRADLE_JVM -> replacePenultimatePaths(java, resources, "main")
                Layout.AMPER -> onlyJavaFragments.filter { !it.isTest }.let {
                    java.setSrcDirs(it.map { it.src })
                    resources.setSrcDirs(emptyList<File>())
                }
                else -> Unit
            }
        }

        javaPE.sourceSets.findByName("test")?.apply {
            when (layout) {
                Layout.GRADLE_JVM -> replacePenultimatePaths(java, resources, "test")
                Layout.AMPER -> onlyJavaFragments.filter { !it.isTest }.let {
                    java.setSrcDirs(it.map { it.src })
                    resources.setSrcDirs(emptyList<File>())
                }
                else -> Unit
            }
        }
    }
}
