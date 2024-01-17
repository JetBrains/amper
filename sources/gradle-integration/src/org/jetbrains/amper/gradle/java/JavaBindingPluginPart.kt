/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.java

import org.gradle.api.JavaVersion
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
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
import org.jetbrains.amper.gradle.java.JavaAmperNamingConvention.amperFragment
import org.jetbrains.amper.gradle.java.JavaAmperNamingConvention.maybeCreateJavaSourceSet
import org.jetbrains.amper.gradle.kmpp.KMPEAware
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.kotlinSourceSet
import org.jetbrains.amper.gradle.kmpp.KotlinAmperNamingConvention.target
import org.jetbrains.amper.gradle.layout
import org.jetbrains.amper.gradle.replacePenultimatePaths
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
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
            with(fragment.target!!) {
                fragment.settings.jvm?.target.let { jvmTarget ->
                    fragment.compilation?.compileTaskProvider?.configure {
                        it as KotlinCompilationTask<KotlinJvmCompilerOptions>
                        it.compilerOptions.jvmTarget.set(jvmTarget?.schemaValue?.let { JvmTarget.fromTarget(it) })
                    }
                }
            }
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
        if (fragment.settings.compose?.enabled != true) {
            project.plugins.apply(ApplicationPlugin::class.java)
        }

        val jvmSettings = fragment.settings.jvm
        val javaSource = fragment.settings.java?.source ?: jvmSettings?.target
        jvmSettings?.target?.let {
            javaPE.targetCompatibility = JavaVersion.toVersion(it.schemaValue)
        }
        javaSource?.let {
            javaPE.sourceCompatibility = JavaVersion.toVersion(it.schemaValue)
        }

        // Do when layout is known.
        project.afterEvaluate {
            if (module.type.isLibrary()) return@afterEvaluate
            val foundMainClass = if (jvmSettings?.mainClass != null) {
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

            @Suppress("OPT_IN_USAGE")
            (fragment.target as KotlinJvmTarget).mainRun {
                mainClass.set(foundMainClass)
            }

            javaAPE?.apply {
                // Check if main class is set in the build script.
                if (mainClass.orNull == null) {
                    mainClass.set(foundMainClass)
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
        project.plugins.apply(JavaPlugin::class.java)

        kotlinMPE.targets.toList().forEach {
            if (it is KotlinJvmTarget) it.withJava()
        }

        // Set sources for all Amper related source sets.
        platformFragments.forEach {
            it.maybeCreateJavaSourceSet()
        }
    }

    private fun adjustSourceDirs() {
        javaPE.sourceSets.all { sourceSet ->
            val fragment = sourceSet.amperFragment
            when {
                // Do GRADLE_JVM specific.
                layout == Layout.GRADLE_JVM -> {
                    if (sourceSet.name == "main") {
                        replacePenultimatePaths(sourceSet.java, sourceSet.resources, "main")
                    } else if (sourceSet.name == "test") {
                        replacePenultimatePaths(sourceSet.java, sourceSet.resources, "test")
                    }
                }

                // Do AMPER specific.
                layout == Layout.AMPER && fragment != null -> {
                    sourceSet.java.setSrcDirs(listOf(fragment.src))
//                    sourceSet.resources.setSrcDirs(listOf(fragment.resourcesPath))
                    sourceSet.resources.setSrcDirs(emptyList<File>())
                }

                layout == Layout.AMPER && fragment == null -> {
                    sourceSet.java.setSrcDirs(emptyList<File>())
                    sourceSet.resources.setSrcDirs(emptyList<File>())
                }
            }
        }
    }
}