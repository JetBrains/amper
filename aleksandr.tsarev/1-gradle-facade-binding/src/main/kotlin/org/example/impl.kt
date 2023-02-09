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
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon
import java.nio.file.Path
import kotlin.io.path.isSameFileAs

var Gradle.knownModel: Model?
    get() = (this as ExtensionAware).extensions.extraProperties["org.example.knownModel"] as? Model
    set(value) {
        (this as ExtensionAware).extensions.extraProperties["org.example.knownModel"] = value
    }

@Suppress("UNCHECKED_CAST")
var Gradle.pathToModuleId: Map<String, String>?
    get() = (this as ExtensionAware).extensions.extraProperties["org.example.pathToModuleId"] as? Map<String, String>
    set(value) {
        (this as ExtensionAware).extensions.extraProperties["org.example.pathToModuleId"] = value
    }

@Suppress("UNCHECKED_CAST")
var Gradle.moduleIdToPath: Map<String, String>?
    get() = (this as ExtensionAware).extensions.extraProperties["org.example.moduleIdToPath"] as? Map<String, String>
    set(value) {
        (this as ExtensionAware).extensions.extraProperties["org.example.moduleIdToPath"] = value
    }

/**
 * Check is module contains only default target.
 */
fun Model.isOnlyKotlinModule(moduleId: String) =
    getTargets(moduleId).size == 1 && getTargets(moduleId).contains(Model.defaultTarget)

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
            if (model.isOnlyKotlinModule(connectedModuleId)) {
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
        val pathToModuleId = project.gradle.pathToModuleId ?: return
        val moduleIdToPath = project.gradle.moduleIdToPath ?: return
        val linkedModuleId = pathToModuleId[project.path] ?: return

        applyModule(model, linkedModuleId, project)
        applyKotlin(model, linkedModuleId, project)

        if (model.isOnlyKotlinModule(linkedModuleId))
            OnlyKotlinModulePlugin(model, linkedModuleId, moduleIdToPath, project).apply()
        else
            KMPPModulePlugin(model, linkedModuleId, moduleIdToPath, project).apply()
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

private fun KotlinDependencyHandler.addDependency(moduleIdToPath: Map<String, String>, dependency: String) {
    if (dependency.startsWith("[local]")) {
        val dependencyModuleId = dependency.removePrefix("[local]")
        val dependencyProjectPath = moduleIdToPath[dependencyModuleId] ?: dependencyModuleId
        implementation(project(dependencyProjectPath))
    } else
        implementation(dependency)
}

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class OnlyKotlinModulePlugin(
    private val model: Model,
    private val moduleId: String,
    private val moduleIdToPath: Map<String, String>,
    project: Project,
) {

    private val kotlinPE = project.extensions.getByType(KotlinProjectExtension::class.java)

    fun apply() {
        // Add dependencies for main source set.
        kotlinPE.sourceSets.getByName("main").dependencies {
            model.getDeclaredDependencies(moduleId, Model.defaultTarget).forEach { dependency ->
                addDependency(moduleIdToPath, dependency)
            }
        }
    }
}

/**
 * Plugin logic, bind to specific module, when multiple targets are available.
 */
class KMPPModulePlugin(
    private val model: Model,
    private val moduleId: String,
    private val moduleIdToPath: Map<String, String>,
    project: Project,
) {

    private val targets by lazy { model.getTargets(moduleId) }

    private val kotlinMPE = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    fun apply() {
        val mainSourceSet = getOrCreateSourceSet("main")

        forSourceSet("commonMain") {
            dependsOn(mainSourceSet)
        }

        // Initialize targets and add dependencies.
        targets.forEach { target ->
            when (target) {
                "jvm" -> configureAndAddDependenciesForTarget(target, "jvmMain") { jvm() }
                "ios" -> configureAndAddDependenciesForTarget(target, "iosMain") { ios() }
                "js" -> configureAndAddDependenciesForTarget(target, "jsMain") { js() }
                Model.defaultTarget -> configureAndAddDependenciesForTarget(target, "main") { }
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
        kotlinMPE.targetSpecific()
        forSourceSet(sourceSetName) {
            dependencies {
                model.getDeclaredDependencies(moduleId, target).forEach { dependency ->
                    addDependency(moduleIdToPath, dependency)
                }
            }
        }
    }

    private fun forSourceSet(sourceSetName: String, action: KotlinSourceSet.() -> Unit) =
        getOrCreateSourceSet(sourceSetName).action()

    private fun getOrCreateSourceSet(sourceSetName: String) =
        kotlinMPE.sourceSets.maybeCreate(sourceSetName)

}