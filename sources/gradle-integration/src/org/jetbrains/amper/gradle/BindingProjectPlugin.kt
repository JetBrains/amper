/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.testing.Test
import org.jetbrains.amper.frontend.PublicationPart
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.schema.JUnitVersion
import org.jetbrains.amper.gradle.android.AndroidBindingPluginPart
import org.jetbrains.amper.gradle.base.BindingPluginPart
import org.jetbrains.amper.gradle.base.PluginPartCtx
import org.jetbrains.amper.gradle.compose.ComposePluginPart
import org.jetbrains.amper.gradle.java.JavaBindingPluginPart
import org.jetbrains.amper.gradle.kmpp.KMPPBindingPluginPart
import org.jetbrains.amper.gradle.kover.KoverPluginPart
import org.jetbrains.amper.gradle.serialization.SerializationPluginPart
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
            org.jetbrains.amper.gradle.apple.AppleBindingPluginPart(pluginCtx),
            SerializationPluginPart(pluginCtx),
            KoverPluginPart(pluginCtx)
        )
        appliedParts = registeredParts.filter { it.needToApply }

        // Apply after evaluate (For example to access Amper extension).
        // But register handlers first, so they will run before all other, that are registered
        // within "beforeEvaluate".
        project.afterEvaluate {
            appliedParts.forEach(BindingPluginPart::applyAfterEvaluate)
        }

        project.extensions.add("amper", AmperGradleExtension(project))

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
        module.leafNonTestFragments.firstOrNull()?.parts?.find<PublicationPart>()?.let {
            // TODO Handle artifacts with different coordinates, or move "PublicationArtifactPart" to module part.
            project.group = it.group ?: ""
            project.version = it.version ?: ""
            extension.repositories.configure(module.parts.find<RepositoriesModulePart>(), all = false)
        }
    }

    /**
     * [all] - if we need to configure all repositories, or only publishing ones.
     */
    private fun RepositoryHandler.configure(part: RepositoriesModulePart?, all: Boolean = true) {
        val repositories = part?.mavenRepositories?.filter { all || it.publish } ?: return
        repositories.forEach { declared ->
            if (declared.id == "mavenLocal" && declared.url == "mavenLocal") {
                mavenLocal()
            } else {
                maven {
                    it.name = declared.id
                    it.url = URI.create(declared.url)
                    if (declared.userName != null && declared.password != null) {
                        it.credentials { cred ->
                            cred.username = declared.userName
                            cred.password = declared.password
                        }
                    }
                }
            }
        }
    }

    private fun applyTest(linkedModule: PotatoModuleWrapper, project: Project) {
        if (linkedModule.leafTestFragments.any { it.settings.junit == JUnitVersion.JUNIT5 }) {
            project.tasks.withType(Test::class.java) {
                // TODO Add more comprehensive support - only enable for those tasks,
                //   that relate to fragment.
                it.useJUnitPlatform()
            }
        }
    }
}