package org.jetbrains.deft.proto.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.testing.Test
import org.jetbrains.deft.proto.frontend.PublicationPart
import org.jetbrains.deft.proto.frontend.RepositoriesModulePart
import org.jetbrains.deft.proto.frontend.TestPart
import org.jetbrains.deft.proto.gradle.android.AndroidBindingPluginPart
import org.jetbrains.deft.proto.gradle.apple.AppleBindingPluginPart
import org.jetbrains.deft.proto.gradle.base.BindingPluginPart
import org.jetbrains.deft.proto.gradle.base.PluginPartCtx
import org.jetbrains.deft.proto.gradle.compose.ComposePluginPart
import org.jetbrains.deft.proto.gradle.java.JavaBindingPluginPart
import org.jetbrains.deft.proto.gradle.kmpp.KMPPBindingPluginPart
import java.net.URI

/**
 * Gradle project plugin entry point.
 */
class BindingProjectPlugin : Plugin<Project> {

    lateinit var appliedParts: List<BindingPluginPart>

    fun onDefExtensionChanged() = appliedParts.forEach { it.onDefExtensionChanged() }

    override fun apply(project: Project) = with(SLF4JProblemReporterContext()) {
        // Prepare context.
        val model = project.gradle.knownModel ?: return
        val projectToModule = project.gradle.projectPathToModule
        val moduleToProject = project.gradle.moduleFilePathToProject
        val linkedModule = projectToModule[project.path] ?: return
        val pluginCtx = PluginPartCtx(project, model, linkedModule, moduleToProject)

        // Find applied parts. Preserve order!
        val kmppBindingPluginPart = KMPPBindingPluginPart(pluginCtx)
        val registeredParts = listOf(
            AndroidBindingPluginPart(pluginCtx),
            kmppBindingPluginPart,
            JavaBindingPluginPart(pluginCtx),
            ComposePluginPart(pluginCtx),
            AppleBindingPluginPart(pluginCtx),
        )
        appliedParts = registeredParts.filter { it.needToApply }

        // Apply after evaluate (For example to access deft extension).
        // But register handlers first, so they will run before all other, that are registered
        // within "beforeEvaluate".
        project.afterEvaluate {
            appliedParts.forEach(BindingPluginPart::applyAfterEvaluate)
        }

        project.extensions.add("deft", DeftGradleExtension(project))

        // Apply before evaluate.
        appliedParts.forEach(BindingPluginPart::applyBeforeEvaluate)
        kmppBindingPluginPart.afterAll()

        // Apply other settings.
        applyRepositoryAttributes(linkedModule, project)
        applyPublicationAttributes(linkedModule, project)
        applyTest(linkedModule, project)

        if (problemReporter.getErrors().isNotEmpty()) {
            throw GradleException(problemReporter.getGradleError())
        }
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
            if (declared.id == "mavenLocal" && declared.url == "mavenLocal") {
                mavenLocal()
            } else {
                maven {
                    it.name = declared.id
                    it.url = URI.create(declared.url)
                    it.credentials { cred ->
                        cred.username = declared.userName
                        cred.password = declared.password
                    }
                }
            }
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