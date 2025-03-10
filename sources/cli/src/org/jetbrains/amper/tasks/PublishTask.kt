/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.codehaus.plexus.PlexusContainer
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.util.repository.AuthenticationBuilder
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenLocalRepository
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.maven.PublicationCoordinatesOverrides
import org.jetbrains.amper.maven.createPlexusContainer
import org.jetbrains.amper.maven.deployToRemoteRepo
import org.jetbrains.amper.maven.installToMavenLocal
import org.jetbrains.amper.maven.merge
import org.jetbrains.amper.maven.publicationCoordinates
import org.jetbrains.amper.maven.toMavenArtifact
import org.jetbrains.amper.maven.writePomFor
import org.jetbrains.amper.tasks.custom.CustomTask
import org.jetbrains.amper.tasks.jvm.JvmClassesJarTask
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.jetbrains.amper.telemetry.useWithoutCoroutines
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.isRegularFile

private val mavenLocalRepository by lazy {
    spanBuilder("Initialize maven local repository").useWithoutCoroutines {
        MavenLocalRepository()
    }
}

class PublishTask(
    override val taskName: TaskName,
    val module: AmperModule,
    val targetRepository: RepositoriesModulePart.Repository,
    private val tempRoot: AmperProjectTempRoot,
) : Task {

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {

        if (!targetRepository.publish) {
            userReadableError(
                "Cannot publish to repository '${targetRepository.id}' because it's not marked as publishable. " +
                        "Please check your configuration and make sure that `publish: true` is set for this repository."
            )
        }

        val localRepositoryPath = mavenLocalRepository.repository
        val artifacts = createArtifactsToDeploy(dependenciesResult)

        /**
         * Publish uses a different code to publish to maven local and to any other remote
         * repositories.
         * Maven local is different, since there are some settings to set it up
         * (https://maven.apache.org/resolver/local-repository.html) and also it does not use checksums
         * https://maven.apache.org/plugins/maven-install-plugin/index.html#important-note-for-version-3-0-0
         * > The `install:install` goal does not support creating checksums anymore via -DcreateChecksum=true cause this
         * > option has been removed. Details can be found in MINSTALL-143.
         */
        spanBuilder("Maven publish").use {
            if (targetRepository.url == "mavenLocal") {
                logger.info("Installing artifacts of module '${module.userReadableName}' to local maven repository at $localRepositoryPath")

                try {
                    container.installToMavenLocal(localRepositoryPath, artifacts)
                } catch (e: Exception) {
                    userReadableError("Couldn't install artifacts of module '${module.userReadableName}' to maven local: $e")
                }
            } else {
                val remoteRepository = targetRepository.toMavenRemoteRepository()

                logger.info("Publishing artifacts of module '${module.userReadableName}' to " +
                        "remote maven repository at ${remoteRepository.url} (id: '${remoteRepository.id}')")

                try {
                    container.deployToRemoteRepo(remoteRepository, localRepositoryPath, artifacts)
                } catch (e: Exception) {
                    userReadableError("Couldn't publish artifacts of module '${module.userReadableName}' to repository '${remoteRepository.id}': $e")
                }
            }
        }

        return Result()
    }

    private fun createArtifactsToDeploy(dependenciesResult: List<TaskResult>): List<Artifact> {
        val overrides = dependenciesResult
            .filterIsInstance<ResolveExternalDependenciesTask.Result>()
            .map { it.coordinateOverridesForPublishing }
            .merge()

        // we only publish JVM for now
        val coords = module.publicationCoordinates(Platform.JVM)
        val pomPath = generatePomFile(module, Platform.JVM, overrides)

        // Note: this will break if we have multiple dependency tasks with the same type, because we'll try to publish
        //  different files with identical artifact coordinates (including classifier).
        return dependenciesResult.flatMap { it.toMavenArtifact(coords) } + pomPath.toMavenArtifact(coords)
    }

    private fun generatePomFile(
        module: AmperModule,
        platform: Platform,
        overrides: PublicationCoordinatesOverrides,
    ): Path {
        tempRoot.path.createDirectories()
        val tempPath = createTempFile(tempRoot.path, "maven-deploy", ".pom")
        tempPath.toFile().deleteOnExit() // FIXME delete the file when done with upload instead of deleteOnExit

        // TODO publish Gradle metadata
        tempPath.writePomFor(module, platform, overrides, gradleMetadataComment = false)
        return tempPath
    }

    private fun TaskResult.toMavenArtifact(coords: MavenCoordinates) = when (this) {
        is JvmClassesJarTask.Result -> listOf(jarPath.toMavenArtifact(coords))
        is SourcesJarTask.Result -> listOf(jarPath.toMavenArtifact(coords, classifier = "sources"))
        is CustomTask.Result -> {
            artifactsToPublish.map { publish ->
                // TODO wildcard matching support?
                val path = outputDirectory.resolve(publish.pathWildcard).normalize()
                if (!path.startsWith(outputDirectory)) {
                    // Should not happen, task output is checked in CustomTask itself
                    error("Task output relative path '${publish.pathWildcard}'" +
                            "must be under task output '$outputDirectory', but got: $path")
                }

                if (!path.isRegularFile()) {
                    // Should not happen, task output is checked in CustomTask itself
                    error("Custom task artifact for publication was not found: $path")
                }

                path.toMavenArtifact(
                    coords = coords,
                    artifactId = publish.artifactId,
                    classifier = publish.classifier,
                    extension = publish.extension,
                )
            }
        }
        is ResolveExternalDependenciesTask.Result, // this is just for coords overrides, not extra artifacts
        is Result -> emptyList()
        else -> error("Unsupported dependency result: ${javaClass.name}")
    }

    private fun RepositoriesModulePart.Repository.toMavenRemoteRepository(): RemoteRepository {
        val builder = RemoteRepository.Builder(id, "default", url)
        if (userName != null && password != null) {
            val authBuilder = AuthenticationBuilder()
            authBuilder.addUsername(userName)
            authBuilder.addPassword(password)
            builder.setAuthentication(authBuilder.build())
        }
        return builder.build()
    }

    class Result : TaskResult

    companion object {
        private val container: PlexusContainer by lazy { createPlexusContainer() }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
