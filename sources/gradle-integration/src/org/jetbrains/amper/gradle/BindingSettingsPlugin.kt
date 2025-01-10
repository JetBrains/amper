/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.get
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModelInit
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.aomBuilder.chooseComposeVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension

/**
 * Gradle setting plugin, that is responsible for:
 * 1. Initializing gradle projects, based on the Amper model.
 * 2. Applying kotlin or KMP plugins.
 * 3. Associate gradle projects with modules.
 */
// This is registered via FQN from the resources in org.jetbrains.amper.settings.plugin.properties
class BindingSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        with(SLF4JProblemReporterContext()) {

            // the class loader is different within projectsLoaded, and we need this one to load the ModelInit service
            val gradleClassLoader = Thread.currentThread().contextClassLoader
            settings.gradle.projectsLoaded {
                // at this point all projects have been created by settings.gradle.kts, but none were evaluated yet
                val projects = settings.gradle.rootProject.allprojects

                val modelResult = ModelInit.getGradleAmperModel(
                    rootProjectDir = settings.rootDir.toPath().toAbsolutePath(),
                    subprojectDirs = projects.map { it.projectDir.toPath().toAbsolutePath() },
                    loader = gradleClassLoader,
                )
                if (modelResult is Result.Failure<Model> || problemReporter.hasFatal) {
                    throw GradleException(problemReporter.getGradleError())
                }


                val model = modelResult.get()

                settings.gradle.knownModel = model
                setGradleProjectsToAmperModuleMappings(projects, model.modules, settings.gradle)

                settings.setupComposePlugin(model)

                projects.forEach { project ->
                    if (project.amperModule != null) {
                        configureProjectForAmper(project)
                    } else if (project === project.rootProject) {
                        // Even if the root project doesn't have a module.yaml file (and thus is not an Amper project),
                        // subprojects using Kotlin/Native add the :commonizeNativeDistribution task to the root.
                        // The IDE runs it, as well as native subproject builds.
                        // Therefore, it needs mavenCentral to resolve kotlin-klib-commonizer-embeddable.
                        project.repositories.mavenCentral()
                    }
                }
            }
        }
    }

    private fun setGradleProjectsToAmperModuleMappings(
        projects: Set<Project>,
        modules: List<AmperModule>,
        gradle: Gradle,
    ) {
        val amperModulesByDir = modules
            .filter { it.hasAmperConfigFile() }
            .associateBy { it.moduleDir.absolutePathString() }

        projects.forEach { project ->
            val module = amperModulesByDir[project.projectDir.absolutePath] ?: return@forEach
            project.amperModule = AmperModuleWrapper(module)
            gradle.moduleFilePathToProjectPath[module.moduleDir] = project.path
        }
    }

    private fun Settings.setupComposePlugin(model: Model) {
        val chosenComposeVersion = chooseComposeVersion(model)
        // We don't need to use the dynamic plugin mechanism if the user wants the embedded Compose version (because
        // it's already on the classpath). Using dynamic plugins relies on unreliable internal Gradle APIs, which are
        // absent in (or incompatible with) recent Gradle versions, so we only use this if absolutely necessary.
        if (chosenComposeVersion != null && chosenComposeVersion != UsedVersions.composeVersion) {
            setupDynamicPlugins(
                "org.jetbrains.compose:compose-gradle-plugin:$chosenComposeVersion",
            ) {
                mavenCentral()
                // For compose dev versions.
                maven { it.setUrl("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
            }
        }
    }

    private fun configureProjectForAmper(project: Project) {

        // Dirty hack related with the same problem as here
        // https://github.com/JetBrains/compose-multiplatform/blob/b6e7ba750c54fddfd60c57b0a113d80873aa3992/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/resources/ComposeResources.kt#L75
        listOf(
            "com.android.application",
            "com.android.library"
        ).forEach {
            project.plugins.withId(it) {
                project.tasks.matching {
                    it is AndroidLintAnalysisTask || it is LintModelWriterTask
                }.configureEach { task ->
                    project.tasks.matching { it.name.startsWith("generateResourceAccessorsFor") }
                        .map { it.name }
                        .forEach {
                            task.mustRunAfter(it)
                        }
                }
            }
        }

        // /!\ This overrides any user configuration from settings.gradle.kts
        // This is only done in modules with Amper's module.yaml config to avoid issues
        project.repositories.addDefaultAmperRepositoriesForDependencies()

        // Disable warning about Default Kotlin Hierarchy.
        project.extraProperties.set("kotlin.mpp.applyDefaultHierarchyTemplate", "false")

        // Apply Kotlin plugins.
        project.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)
        project.plugins.apply(BindingProjectPlugin::class.java)

        project.afterEvaluate {
            // W/A for XML factories mess within apple plugin classpath.
            val hasAndroidPlugin = it.plugins.hasPlugin("com.android.application") ||
                    it.plugins.hasPlugin("com.android.library")
            if (hasAndroidPlugin) {
                adjustXmlFactories()
            }
        }
    }
}

private fun AmperModule.hasAmperConfigFile() =
    (source as? AmperModuleFileSource)?.buildFile?.nameWithoutExtension == "module"

private fun RepositoryHandler.addDefaultAmperRepositoriesForDependencies() {
    mavenCentral()
    // For the Android plugin and dependencies
    google()
    // For other Gradle plugins
    gradlePluginPortal()
    // For dev versions of kotlin
    maven { it.setUrl("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") }
    // For dev versions of compose plugin and dependencies
    maven { it.setUrl("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
}
