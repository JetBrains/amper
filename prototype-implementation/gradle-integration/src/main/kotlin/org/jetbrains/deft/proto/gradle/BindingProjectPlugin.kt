package org.jetbrains.deft.proto.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.gradle.android.applyAndroidAttributes
import org.jetbrains.deft.proto.gradle.java.applyJavaAttributes
import org.jetbrains.deft.proto.gradle.kmpp.applyKotlinMPAttributes

/**
 * Gradle project plugin entry point.
 */
class BindingProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val model = project.gradle.knownModel ?: return
        val projectToModule = project.gradle.projectPathToModule
        val moduleToProject = project.gradle.moduleFilePathToProject
        val linkedModule = projectToModule[project.path] ?: return
        val pluginCtx = PluginPartCtx(project, model, linkedModule, moduleToProject)

        applyKotlinMPAttributes(pluginCtx)
        if (linkedModule.androidNeeded) applyAndroidAttributes(pluginCtx)
        if (linkedModule.javaNeeded) applyJavaAttributes(pluginCtx)
        applyPublicationAttributes(linkedModule, project)
    }

    private fun applyPublicationAttributes(potatoModule: PotatoModule, project: Project) {

    }
}