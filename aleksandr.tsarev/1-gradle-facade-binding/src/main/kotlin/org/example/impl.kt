package org.example

import com.android.build.gradle.api.AndroidBasePlugin
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import org.example.api.Model
import org.example.api.ModelInit
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon
import java.nio.file.Path
import kotlin.io.path.isSameFileAs


/**
 * Gradle setting plugin, that is responsible for:
 * 1. Initializing gradle projects, based on model.
 * 2. Applying kotlin or KMP plugins.
 * 3. Associate gradle projects with modules.
 */
@Suppress("unused")
class BindingSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val rootPath = settings.rootDir.toPath().toAbsolutePath()
        val model = ModelInit.getModel(rootPath)
        val sortedByPathModules = model.modules.sortedBy { it.second }

        // Fallback if no modules.
        if (sortedByPathModules.isEmpty()) return

        // Need to create root project if no module reside in root.
        if (sortedByPathModules[0].second != rootPath) {
            settings.include(":")
            settings.project(":").projectDir = rootPath.toFile()
        }

        // Function to create valid gradle project paths.
        val path2ProjectPath = mutableMapOf<Path, String>()
        fun getProjectPathAndMemoize(currentModulePath: Path, currentModuleId: String): String {
            // Check if we are root module.
            if (currentModulePath.isSameFileAs(rootPath)) return ":"
            // Get previous known project path.
            var previousPath: Path = currentModulePath
            while (!path2ProjectPath.containsKey(previousPath) && !previousPath.isSameFileAs(rootPath))
                previousPath = Path.of("/").resolve(previousPath.subpath(0, previousPath.nameCount - 1))
            val previousNonRootProjectPath = path2ProjectPath[previousPath]
            // Calc current project path, based on file structure.
            val resultProjectPath = if (previousNonRootProjectPath != null) {
                "$previousNonRootProjectPath:$currentModuleId"
            } else {
                ":$currentModuleId"
            }
            // Memoize.
            path2ProjectPath[currentModulePath] = resultProjectPath
            return resultProjectPath
        }

        val pathToModuleId = mutableMapOf<String, String>()
        val moduleIdToPath = mutableMapOf<String, String>()

        // Go by ascending path length and generate projects.
        sortedByPathModules.forEach {
            val projectPath = getProjectPathAndMemoize(it.second, it.first)
            settings.include(projectPath)
            val project = settings.project(projectPath)
            project.projectDir = it.second.toFile()
            pathToModuleId[projectPath] = it.first
            moduleIdToPath[it.first] = projectPath
        }

        // Associate gradle projects with modules.
        settings.gradle.pathToModuleId = pathToModuleId
        settings.gradle.moduleIdToPath = moduleIdToPath
        settings.gradle.knownModel = model

        // Initialize plugins for each module.
        settings.gradle.beforeProject {
            // Add repositories for KMPP to work.
            it.repositories.google()
            it.repositories.jcenter()

            // Can be empty for root.
            val connectedModuleId = pathToModuleId[it.path] ?: return@beforeProject

            // Apply Kotlin plugins.
            if (model.isOnlyKotlinModule(connectedModuleId)) {
                it.plugins.apply(KotlinPluginWrapper::class.java)
            } else {
                it.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)
            }

            // Apply Android plugin.
            if (model.hasAndroid(connectedModuleId)) {
                if (model.isApplication(connectedModuleId)) {
                    it.plugins.apply(AppPlugin::class.java)
                } else {
                    it.plugins.apply(LibraryPlugin::class.java)
                }
            }

            it.plugins.apply(BindingProjectPlugin::class.java)
        }
    }
}

/**
 * Gradle project plugin entry point.
 */
class BindingProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val model = project.gradle.knownModel ?: return
        val pathToModuleId = project.gradle.pathToModuleId ?: return
        val moduleIdToPath = project.gradle.moduleIdToPath ?: return
        val linkedModuleId = pathToModuleId[project.path] ?: return
        val pluginCtx = ModulePluginCtx(project, model, linkedModuleId, moduleIdToPath)

        applyModule(model, linkedModuleId, project)
        applyKotlin(model, linkedModuleId, project)

        if (model.isOnlyKotlinModule(linkedModuleId))
            OnlyKotlinModulePlugin(pluginCtx).apply()
        else
            KMPPModulePlugin(pluginCtx).apply()

        if (model.hasAndroid(linkedModuleId)) {
            AndroidModulePlugin(pluginCtx).apply()
        }
    }

    private fun applyModule(model: Model, moduleId: String, project: Project) {
        val moduleInfo = model.getModuleInfo(moduleId)
        val specifiedGroup = moduleInfo["group"]?.first()
        specifiedGroup?.let { project.group = it }
        val specifiedVersion = moduleInfo["version"]?.first()
        specifiedVersion?.let { project.version = it }

        if (specifiedGroup != null && specifiedVersion != null) {
            // Do publish configuration here.
        }
    }

    private fun applyKotlin(model: Model, moduleId: String, project: Project) {
        val kotlinInfo = model.getKotlinInfo(moduleId)
        project.tasks.withType(KotlinCompile::class.java).forEach { task ->
            task.kotlinOptions {
                kotlinInfo["freeCompilerArgs"]?.let { freeCompilerArgs += it }
                kotlinInfo["apiVersion"]?.first()?.let { apiVersion = it }
                kotlinInfo["languageVersion"]?.first()?.let { languageVersion = it }
            }
        }

        project.tasks.withType(KotlinCompileCommon::class.java).forEach { task ->
            task.kotlinOptions {
                kotlinInfo["freeCompilerArgs"]?.let { freeCompilerArgs += it }
                kotlinInfo["apiVersion"]?.first()?.let { apiVersion = it }
                kotlinInfo["languageVersion"]?.first()?.let { languageVersion = it }
            }
        }
    }
}
