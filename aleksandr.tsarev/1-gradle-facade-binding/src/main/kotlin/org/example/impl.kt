package org.example

import org.example.api.Model
import org.example.api.ModelInit
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import java.nio.file.Path

var Gradle.knownModel: Model?
    get() = (this as ExtensionAware).extensions.extraProperties["org.example.knownModel"] as? Model
    set(value) {
        (this as ExtensionAware).extensions.extraProperties["org.example.knownModel"] = value
    }

@Suppress("UNCHECKED_CAST")
var Gradle.moduleIdMap: Map<String, String>?
    get() = (this as ExtensionAware).extensions.extraProperties["org.example.linkedModuleId"] as? Map<String, String>
    set(value) {
        (this as ExtensionAware).extensions.extraProperties["org.example.linkedModuleId"] = value
    }

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

        val moduleIdMap = mutableMapOf<String, String>()

        // Function to create valid gradle project paths.
        val path2ProjectPath = mutableMapOf<Path, String>()
        fun getProjectPathAndMemoize(currentModulePath: Path, currentModuleId: String): String {
            // Check if we are root module.
            if (currentModulePath == rootPath) return ":"
            // Get previous known project path.
            var previousPath: Path = currentModulePath
            while (!path2ProjectPath.containsKey(previousPath) && previousPath != rootPath)
                previousPath = previousPath.subpath(0, currentModulePath.nameCount - 1).toAbsolutePath()
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

        // Go by ascending path length and generate projects.
        model.modules.sortedBy { it.second }.forEach {
            val projectPath = getProjectPathAndMemoize(it.second, it.first)
            settings.include(projectPath)
            val project = settings.project(projectPath)
            project.projectDir = it.second.toFile()
            moduleIdMap[projectPath] = it.first
        }

        // Associate gradle projects with modules.
        settings.gradle.moduleIdMap = moduleIdMap
        settings.gradle.knownModel = model

        // Initialize plugins for each module.
        settings.gradle.beforeProject {
            val connectedModuleId = moduleIdMap[it.path]!!
            if (model.getTargets(connectedModuleId).size == 1 && model.getTargets(connectedModuleId)
                    .contains(Model.defaultTarget)
            ) {
                it.plugins.apply(KotlinPluginWrapper::class.java)
            } else {
                it.plugins.apply(KotlinMultiplatformPluginWrapper::class.java)
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
        val moduleIdMap = project.gradle.moduleIdMap ?: return
        val linkedModuleId = moduleIdMap[project.path] ?: return
        ModulePlugin(model, linkedModuleId, project).apply()
    }
}

/**
 * Plugin logic, bind to specific module.
 */
class ModulePlugin(
    private val model: Model,
    private val moduleId: String,
    private val project: Project,
) {

    private val targets by lazy { model.getTargets(moduleId) }

    private val projectExtensions = project.extensions

    fun apply() {
        // Add repositories for KMPP to work.
        project.repositories.google()
        project.repositories.jcenter()

        // Initialize targets and add dependencies.
        targets.forEach { target ->
            when (target) {
                "jvm" -> configureAndAddDependenciesForTarget(target, "jvmMain") { jvm() }
                "ios" -> configureAndAddDependenciesForTarget(target, "iosMain") { ios() }
                "js" -> configureAndAddDependenciesForTarget(target, "jsMain") { js() }
                Model.defaultTarget -> configureAndAddDependenciesForTarget(target, "commonMain") { }
            }
        }
    }

    /**
     * Perform specific configurations for module targets and add dependencies.
     */
    private fun configureAndAddDependenciesForTarget(
        target: String,
        sourceSetName: String,
        targetSpecific: KotlinMultiplatformExtension.() -> Unit
    ) {
        projectExtensions.configure(KotlinMultiplatformExtension::class.java) { kmpp ->
            kmpp.targetSpecific()
            val sourceSet = projectExtensions.getByType(KotlinProjectExtension::class.java).sourceSets.getByName(sourceSetName)
            sourceSet.dependencies {
                model.getDeclaredDependencies(moduleId, target).forEach { dependency ->
                    this.implementation(dependency)
                }
            }
        }
    }
}