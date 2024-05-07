/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.aomBuilder.chooseComposeVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import kotlin.io.path.extension


/**
 * Gradle setting plugin, that is responsible for:
 * 1. Initializing gradle projects, based on a model.
 * 2. Applying kotlin or KMP plugins.
 * 3. Associate gradle projects with modules.
 */
class SettingsPluginRun(
    private val settings: Settings,
    private val model: Model,
) {

    context(ProblemReporterContext)
    fun run() {
        settings.gradle.knownModel = model

        // Adjust compose plugin dynamically.
        val chosenComposeVersion = chooseComposeVersion(model)
        if (chosenComposeVersion != null) {
            settings.pluginManagement {
                it.plugins.id("")
            }
            settings.setupDynamicPlugins(
                "org.jetbrains.compose:compose-gradle-plugin:$chosenComposeVersion",
            ) {
                mavenCentral()
                // For compose dev versions.
                maven { it.setUrl("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
            }
        }

        initProjects(settings, model)

        // Initialize plugins for each module.
        settings.gradle.beforeProject { project ->
            configureProject(project)
        }
    }

    private fun configureProject(project: Project) {
        // Gradle projects that are not in the map aren't Amper projects (modules) anyway,
        // so we can stop here
        val connectedModule = settings.gradle.projectPathToModule[project.path] ?: run {
            if (project.path == project.rootProject.path) {
                // Add default repositories to the root project, it is required for further applying kmp plugin
                project.repositories.addDefaultAmperRepositoriesForDependencies()
            }
            return
        }
        if (!connectedModule.hasAmperConfigFile()) {
            // we don't want to alter non-Amper subprojects
            return
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

private fun PotatoModuleWrapper.hasAmperConfigFile() = buildFile.extension == "yaml"

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
