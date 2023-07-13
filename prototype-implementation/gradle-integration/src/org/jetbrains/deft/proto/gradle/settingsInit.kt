package org.jetbrains.deft.proto.gradle

import org.gradle.api.initialization.Settings
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.ModelInit
import org.jetbrains.deft.proto.frontend.PotatoModuleType
import org.jetbrains.deft.proto.frontend.propagate.resolved
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText


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
        val model = ModelInit.getModel(rootPath).resolved
        settings.gradle.knownModel = model
        initProjects(settings, model)

        // Search for additional plugins to apply.
        val foundPlugins = model.modules.flatMap { module ->
            val additionalScript = module.additionalScript
            val foundPlugins = additionalScript?.let { parseAdditionalScript(it) }
            foundPlugins ?: emptyList()
        }.toMap()
        settings.pluginManagement { pluginMgmnt ->
            foundPlugins.forEach {
                if (it.value != null) {
                    pluginMgmnt.plugins.id(it.key).version(it.value!!)
                }
            }
        }

        // Initialize plugins for each module.
        settings.gradle.beforeProject { project ->
            // Add repositories for KMPP to work.
            project.repositories.google()
            project.repositories.jcenter()


            // Can be empty for root.
            val connectedModule = settings.gradle.projectPathToModule[project.path] ?: return@beforeProject

            // Apply Android plugin.
            if (connectedModule.androidNeeded) when (connectedModule.type) {
                PotatoModuleType.APPLICATION -> project.plugins.apply("com.android.application")
                PotatoModuleType.LIBRARY -> project.plugins.apply("com.android.library")
            }

            // Apply additional plugins.
            val foundProjectPlugins = connectedModule.additionalScript
                ?.let { parseAdditionalScript(it) }
            foundProjectPlugins?.forEach { project.plugins.apply(it.first) }

            // Apply Kotlin plugins.
            if (connectedModule.buildFile.extension == "yaml") {
                project.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)
            }

            // Apply Binding plugin if there is Pot file.
            if (connectedModule.buildFile.extension == "yaml") {
                project.plugins.apply(BindingProjectPlugin::class.java)
            }
        }
    }
}

private val pluginApplicationRegex = "\\/\\/ plugin (\\S+)( version (\\S+))?\n".toRegex()

fun parseAdditionalScript(script: Path): List<Pair<String, String?>> {
    val matches = pluginApplicationRegex.findAll(script.readText())
    val foundPlugins = matches.map {
        val pluginId = it.groupValues[1]
        val pluginVersion = if (it.groupValues.size > 3) it.groupValues[3] else null
        pluginId to pluginVersion
    }.toList()
    return foundPlugins
}