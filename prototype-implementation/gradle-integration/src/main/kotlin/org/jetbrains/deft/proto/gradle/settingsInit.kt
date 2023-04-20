package org.jetbrains.deft.proto.gradle

import org.gradle.api.initialization.Settings
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.ModelInit
import org.jetbrains.deft.proto.frontend.PotatoModuleType
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper


/**
 * Gradle setting plugin, that is responsible for:
 * 1. Initializing gradle projects, based on model.
 * 2. Applying kotlin or KMP plugins.
 * 3. Associate gradle projects with modules.
 */
@Suppress("unused")
class SettingsPluginRun(
    private val settings: Settings,
    private val model: Model,
) {

    fun run() {
        val rootPath = settings.rootDir.toPath().toAbsolutePath()
        val model = ModelInit.getModel(rootPath)
        settings.gradle.knownModel = model
        initProjects(settings, model)

        // Initialize plugins for each module.
        settings.gradle.beforeProject {
            // Add repositories for KMPP to work.
            it.repositories.google()
            it.repositories.jcenter()

            // Can be empty for root.
            val connectedModule = settings.gradle.projectPathToModule[it.path] ?: return@beforeProject

            // Apply Kotlin plugins.
            it.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)

            // Apply Android plugin.
            if (connectedModule.androidNeeded) when (connectedModule.type) {
                PotatoModuleType.APPLICATION -> it.plugins.apply("com.android.application")
                PotatoModuleType.LIBRARY -> it.plugins.apply("com.android.library")
            }

            // Apply Java application plugin.
            if (connectedModule.javaNeeded) when (connectedModule.type) {
                PotatoModuleType.APPLICATION -> it.plugins.apply("application")
                else -> Unit
            }

            it.plugins.apply(BindingProjectPlugin::class.java)
        }
    }
}

