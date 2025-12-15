/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import com.google.common.collect.ImmutableList
import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.execution.DefaultMavenExecutionResult
import org.apache.maven.execution.MavenExecutionRequestPopulator
import org.apache.maven.execution.MavenSession
import org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory
import org.apache.maven.project.MavenProject
import org.apache.maven.project.ProjectBuilder
import org.apache.maven.properties.internal.EnvironmentUtils
import org.apache.maven.settings.MavenSettingsBuilder
import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.classworlds.ClassWorld
import java.nio.file.Path
import java.util.*

/**
 * Inspired by https://github.com/gradle/gradle/blob/9139c5f77d30b0a7d37b87a504f853d23b12d5d0/platforms/software/build-init/src/main/java/org/gradle/unexported/buildinit/plugins/internal/maven/MavenProjectsCreator.java
 */
internal object MavenModelReader {
    internal fun getReactorProjects(pom: Path, javaVersion: String = "17"): Set<MavenProject> {
        val containerConfiguration = DefaultContainerConfiguration().apply {
            classWorld = ClassWorld("plexus.core", MavenModelReader::class.java.classLoader)
            setName("mavenCore")
            classPathScanning = PlexusConstants.SCANNING_INDEX
            autoWiring = true
        }

        val container = DefaultPlexusContainer(containerConfiguration)
        val builder = container.lookup(ProjectBuilder::class.java)

        // todo: Populate user settings, it's needed for repository authentication information
        val mavenExecutionRequest = DefaultMavenExecutionRequest().apply {
            val settingsBuilder = container.lookup(MavenSettingsBuilder::class.java)
            val settings = settingsBuilder.buildSettings()
            val populator = container.lookup(MavenExecutionRequestPopulator::class.java)
            populator.populateDefaults(this)
            populator.populateFromSettings(this, settings)
        }

        val buildingRequest = mavenExecutionRequest.projectBuildingRequest.apply {
            // todo: AMPER-4942 the proper thing here to do is to extract system propeties from the JDK which is located in
            //  JAVA_HOME because it's a JDK that maven uses
            val mavenSystemProperties = System.getProperties() ?: Properties()
            EnvironmentUtils.addEnvVars(mavenSystemProperties)
            systemProperties = mavenSystemProperties

            val repositoryFactory = container.lookup(DefaultRepositorySystemSessionFactory::class.java)
            repositorySession = repositoryFactory.newRepositorySession(mavenExecutionRequest)
            isProcessPlugins = false
        }

        val mavenProject = builder.build(pom.toFile(), buildingRequest).project
        buildingRequest.project = mavenProject

        val reactorProjects = buildSet {
            add(mavenProject)
            val allProjects = builder.build(ImmutableList.of(pom.toFile()), true, buildingRequest)
            addAll(allProjects.map { it.project })
        }

        val result = DefaultMavenExecutionResult().apply {
            project = mavenProject
        }

        MavenSession(container, buildingRequest.repositorySession, mavenExecutionRequest, result).apply {
            currentProject = mavenProject
        }

        return reactorProjects
    }
}
