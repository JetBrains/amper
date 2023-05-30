package org.jetbrains.deft.proto.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.PublicationArtifactPart
import org.jetbrains.deft.proto.frontend.RepositoriesModelPart
import org.jetbrains.deft.proto.frontend.TestFragmentPart
import org.jetbrains.deft.proto.gradle.android.applyAndroidAttributes
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.java.applyJavaAttributes
import org.jetbrains.deft.proto.gradle.kmpp.applyKotlinMPAttributes
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
        applyKotlinMPAttributes(pluginCtx)
        if (linkedModule.androidNeeded) applyAndroidAttributes(pluginCtx)
        if (linkedModule.javaNeeded) applyJavaAttributes(pluginCtx)

        applyRepositoryAttributes(model, project)
        applyPublicationAttributes(model, linkedModule, project)
        applyTest(linkedModule, project)
        applyAdditionalScript(project, linkedModule)
    }

    private fun applyRepositoryAttributes(
        model: Model,
        project: Project
    ) {
        project.repositories.configure(model.parts.find<RepositoriesModelPart>())
    }

    private fun applyPublicationAttributes(
        model: Model,
        potatoModule: PotatoModuleWrapper,
        project: Project
    ) {
        project.plugins.apply("maven-publish")
        potatoModule.artifacts.firstOrNull { !it.isTest }?.parts?.find<PublicationArtifactPart>()?.let {
            project.group = it.group
            project.version = it.version
            val extension = project.extensions.getByType(PublishingExtension::class.java)
            extension.repositories.configure(model.parts.find<RepositoriesModelPart>())
            extension.publications {
                it.create(project.name.replace("+", "-"), MavenPublication::class.java) {
                    it.from(project.components.findByName("kotlin"))
                }
            }
        }
    }

    private fun RepositoryHandler.configure(part: RepositoriesModelPart?) {
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
        if (linkedModule.fragments.mapNotNull { it.parts.find<TestFragmentPart>() }.any { it.junitPlatform == true }) {
            project.tasks.withType(Test::class.java) {
                it.useJUnitPlatform()
            }
        }
    }
}