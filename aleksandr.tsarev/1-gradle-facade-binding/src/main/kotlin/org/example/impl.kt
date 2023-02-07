package org.example

import org.example.api.Model
import org.example.api.ModelInit
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin

var Gradle.knownModel: Model?
    get() = (this as ExtensionAware).extensions.extraProperties["org.example.knownModel"] as? Model
    set(value) { (this as ExtensionAware).extensions.extraProperties["org.example.knownModel"] = value }

@Suppress("UNCHECKED_CAST")
var Gradle.moduleIdMap: Map<String, String>?
    get() = (this as ExtensionAware).extensions.extraProperties["org.example.linkedModuleId"] as? Map<String, String>
    set(value) { (this as ExtensionAware).extensions.extraProperties["org.example.linkedModuleId"] = value }

@Suppress("unused")
class BindingSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val model = ModelInit.getModel(settings.rootDir.toPath())

        val moduleIdMap = mutableMapOf<String, String>()

        model.modules.forEach {
            val projectPath = ":"
            settings.include(projectPath)
            val project = settings.project(projectPath)
            project.projectDir = it.second.toFile()
            moduleIdMap[projectPath] = it.first
        }

        settings.gradle.moduleIdMap = moduleIdMap
        settings.gradle.knownModel = model

        settings.gradle.beforeProject {
            it.plugins.apply(JavaPlugin::class.java)
            it.plugins.apply(BindingProjectPlugin::class.java)
        }

    }
}

class BindingProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val model = project.gradle.knownModel ?: return
        val moduleIdMap = project.gradle.moduleIdMap ?: return
        val linkedModuleId = moduleIdMap[project.path] ?: return

        val targets = model.getTargets(linkedModuleId)
        targets.forEach { targetId ->
            model.getDeclaredDependencies(linkedModuleId, targetId).forEach { dependencyNotation ->
                project.dependencies.add("implementation", dependencyNotation)
            }
        }
    }
}