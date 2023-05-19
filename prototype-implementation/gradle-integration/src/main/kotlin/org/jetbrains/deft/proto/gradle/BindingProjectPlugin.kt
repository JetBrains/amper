package org.jetbrains.deft.proto.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.deft.proto.frontend.PublicationArtifactPart
import org.jetbrains.deft.proto.gradle.android.applyAndroidAttributes
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
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

    private fun applyPublicationAttributes(potatoModule: PotatoModuleWrapper, project: Project) {
        potatoModule.artifacts.firstOrNull { !it.isTest }?.part<PublicationArtifactPart>()?.let {
            project.group = it.group
            project.version = it.version
            val extension = project.extensions.getByType(PublishingExtension::class.java)
            extension.publications {
                it.create(project.name, MavenPublication::class.java) {
                    it.from(project.components.findByName("kotlin"))
                }
            }
        }
    }
}