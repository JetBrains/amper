/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.apache.maven.bridge.MavenRepositorySystem
import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory
import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.classworlds.ClassWorld
import org.codehaus.plexus.logging.AbstractLogger
import org.codehaus.plexus.logging.BaseLoggerManager
import org.codehaus.plexus.logging.Logger
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.deployment.DeployRequest
import org.eclipse.aether.installation.InstallRequest
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.util.repository.AuthenticationBuilder
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.dependency.resolution.MavenLocalRepository
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.maven.MavenCoordinates
import org.jetbrains.amper.maven.publicationCoordinates
import org.jetbrains.amper.maven.toMavenArtifact
import org.jetbrains.amper.maven.writePomFor
import org.jetbrains.amper.tasks.SourcesJarTask.SourcesJarTaskResult
import org.jetbrains.amper.tasks.jvm.JvmClassesJarTask.JvmClassesJarTaskResult
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempFile

class PublishTask(
    override val taskName: TaskName,
    val module: PotatoModule,
    val targetRepository: RepositoriesModulePart.Repository,
    private val tempRoot: AmperProjectTempRoot,
    private val mavenLocalRepository: MavenLocalRepository,
) : Task {

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {

        val mavenRepositorySystem = container.lookup(MavenRepositorySystem::class.java)
        val defaultRepositorySystemSessionFactory = container.lookup(DefaultRepositorySystemSessionFactory::class.java)

        val localRepositoryPath = mavenLocalRepository.repository.toFile()

        val repositorySystem = container.lookup(RepositorySystem::class.java)
        val request = DefaultMavenExecutionRequest()
        val localRepository = mavenRepositorySystem.createLocalRepository(request, localRepositoryPath)
        request.setLocalRepository(localRepository)
        val repositorySession = defaultRepositorySystemSessionFactory.newRepositorySession(request)
        repositorySession.setConfigProperty(Maven2RepositoryLayoutFactory.CONFIG_PROP_CHECKSUMS_ALGORITHMS, "MD5,SHA-1,SHA-256,SHA-512")

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
        if (targetRepository.url == "mavenLocal") {
            logger.info("Installing artifacts of '${module.userReadableName}' to local maven repository at $localRepositoryPath")

            val installRequest = InstallRequest()
            for (artifact in artifacts) {
                installRequest.addArtifact(artifact)
            }

            repositorySystem.install(repositorySession, installRequest)
        } else {
            val deployRequest = DeployRequest()

            val builder = RemoteRepository.Builder(targetRepository.id, "default", targetRepository.url)
            if (targetRepository.userName != null && targetRepository.password != null) {
                val authBuilder = AuthenticationBuilder()
                authBuilder.addUsername(targetRepository.userName)
                authBuilder.addPassword(targetRepository.password)
                builder.setAuthentication(authBuilder.build())
            }
            val remoteRepository = builder.build()

            logger.info("Deploying artifacts of '${module.userReadableName}' to " +
                    "remote maven repository at ${remoteRepository.url} (id: '${remoteRepository.id}')")

            deployRequest.repository = remoteRepository

            for (artifact in artifacts) {
                deployRequest.addArtifact(artifact)
            }

            repositorySystem.deploy(repositorySession, deployRequest)
        }

        return PublishTaskResult(dependenciesResult)
    }

    private fun createArtifactsToDeploy(dependenciesResult: List<TaskResult>): List<Artifact> {
        // we only publish JVM for now
        val coords = module.publicationCoordinates(Platform.JVM)
        val pomPath = generatePomFile(module, Platform.JVM)

        // Note: this will break if we have multiple dependency tasks with the same type, because we'll try to publish
        //  different files with identical artifact coordinates (including classifier).
        return dependenciesResult.mapNotNull { it.toMavenArtifact(coords) } + pomPath.toMavenArtifact(coords)
    }

    private fun generatePomFile(module: PotatoModule, platform: Platform): Path {
        tempRoot.path.createDirectories()
        val tempPath = createTempFile(tempRoot.path, "maven-deploy", ".pom")
        tempPath.toFile().deleteOnExit() // FIXME delete the file when done with upload instead of deleteOnExit

        // TODO publish Gradle metadata
        tempPath.writePomFor(module, platform, gradleMetadataComment = false)
        return tempPath
    }

    private fun TaskResult.toMavenArtifact(coords: MavenCoordinates) = when (this) {
        is JvmClassesJarTaskResult -> jarPath.toMavenArtifact(coords)
        is SourcesJarTaskResult -> jarPath.toMavenArtifact(coords, classifier = "sources")
        is PublishTaskResult -> null
        else -> error("Unsupported dependency result: ${javaClass.name}")
    }

    class PublishTaskResult(override val dependencies: List<TaskResult>) : TaskResult

    companion object {
        private val container: PlexusContainer by lazy {
            val containerConfiguration = DefaultContainerConfiguration()
                .setClassWorld(ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader()))
                .setName("mavenCore")
                .setClassPathScanning(PlexusConstants.SCANNING_INDEX)
                .setAutoWiring(true)
            val loggerManager = object : BaseLoggerManager() {
                init {
                    threshold = Logger.LEVEL_DEBUG
                }

                override fun createLogger(name: String): Logger = object: AbstractLogger(Logger.LEVEL_DEBUG, name) {
                    private val logger = LoggerFactory.getLogger(name)

                    override fun debug(message: String, throwable: Throwable?) = logger.debug(message, throwable)
                    override fun info(message: String, throwable: Throwable?) = logger.info(message, throwable)
                    override fun warn(message: String, throwable: Throwable?) = logger.warn(message, throwable)
                    override fun error(message: String, throwable: Throwable?) = logger.error(message, throwable)
                    override fun fatalError(message: String, throwable: Throwable?) = logger.error(message, throwable)
                    override fun getChildLogger(name: String): Logger = this
                }
            }

            DefaultPlexusContainer(containerConfiguration)
                .also { it.loggerManager = loggerManager }
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}
