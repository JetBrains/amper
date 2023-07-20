package org.jetbrains.deft.proto.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.testing.Test
import org.jetbrains.deft.proto.frontend.PublicationPart
import org.jetbrains.deft.proto.frontend.RepositoriesModulePart
import org.jetbrains.deft.proto.frontend.TestPart
import org.jetbrains.deft.proto.gradle.android.applyAndroidAttributes
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.java.applyJavaAttributes
import org.jetbrains.deft.proto.gradle.kmpp.KMPPBindingPluginPart
import java.net.URI
import kotlin.io.path.absolutePathString

/**
 * Gradle project plugin entry point.
 */
class BindingProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Prepare context.
        val model = project.gradle.knownModel ?: return
        val projectToModule = project.gradle.projectPathToModule
        val moduleToProject = project.gradle.moduleFilePathToProject
        val linkedModule = projectToModule[project.path] ?: return
        val pluginCtx = PluginPartCtx(project, model, linkedModule, moduleToProject)

        // Apply parts.
        if (linkedModule.androidNeeded) applyAndroidAttributes(pluginCtx)
        val kmppBindingPluginPart = KMPPBindingPluginPart(pluginCtx)
        kmppBindingPluginPart.apply()
        if (linkedModule.javaNeeded) applyJavaAttributes(pluginCtx)
        kmppBindingPluginPart.afterAll()

        applyRepositoryAttributes(linkedModule, project)
        applyPublicationAttributes(linkedModule, project)
        applyTest(linkedModule, project)
        applyAdditionalScript(project, linkedModule)
    }

    private fun applyRepositoryAttributes(
        module: PotatoModuleWrapper,
        project: Project
    ) {
        project.repositories.configure(module.parts.find<RepositoriesModulePart>())
    }

    private fun applyPublicationAttributes(
        module: PotatoModuleWrapper,
        project: Project
    ) {
        project.plugins.apply("maven-publish")
        val extension = project.extensions.getByType(PublishingExtension::class.java)
        module.leafNonTestFragments.firstOrNull { !it.isTest }?.parts?.find<PublicationPart>()?.let {
            // TODO Handle artifacts with different coordinates, or move "PublicationArtifactPart" to module part.
            project.group = it.group ?: ""
            project.version = it.version ?: ""
            extension.repositories.configure(module.parts.find<RepositoriesModulePart>())
        }
    }

    private fun RepositoryHandler.configure(part: RepositoriesModulePart?) {
        val repositories = part?.mavenRepositories?.filter { it.publish } ?: return
        repositories.forEach { declared ->
            if (declared.name == "mavenLocal" && declared.url == "mavenLocal") {
                mavenLocal()
            } else {
                maven {
                    it.name = declared.name
                    it.url = URI.create(declared.url)
                    it.credentials { cred ->
                        cred.username = declared.userName
                        cred.password = declared.password
                    }
                }
            }
        }
    }

    private fun applyAdditionalScript(project: Project, linkedModule: PotatoModuleWrapper) {
        linkedModule.additionalScript?.apply {
            project.apply(mapOf("from" to absolutePathString()))
        }
    }

    private fun applyTest(linkedModule: PotatoModuleWrapper, project: Project) {
        if (linkedModule.leafTestFragments.mapNotNull { it.parts.find<TestPart>() }.any { it.junitPlatform == true }) {
            project.tasks.withType(Test::class.java) {
                it.useJUnitPlatform()
            }
        }
    }
}