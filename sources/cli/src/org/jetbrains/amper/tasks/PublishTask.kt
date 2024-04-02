/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.apache.maven.bridge.MavenRepositorySystem
import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.classworlds.ClassWorld
import org.codehaus.plexus.logging.AbstractLogger
import org.codehaus.plexus.logging.BaseLoggerManager
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.util.WriterFactory
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
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
import org.jetbrains.amper.tasks.SourcesJarTask.SourcesJarTaskResult
import org.jetbrains.amper.tasks.jvm.JvmClassesJarTask.JvmClassesJarTaskResult
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createDirectories

class PublishTask(
    override val taskName: TaskName,
    val module: PotatoModule,
    private val targetRepository: RepositoriesModulePart.Repository,
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

        return object : TaskResult {
            override val dependencies: List<TaskResult>
                get() = dependenciesResult
        }
    }

    private fun createArtifactsToDeploy(dependenciesResult: List<TaskResult>): List<Artifact> {
        val jvmFragment = module.leafFragments.single { !it.isTest && it.platforms.contains(Platform.JVM) }

        val groupId = jvmFragment.settings.publishing?.group ?: error("group must be specified")
        // TODO It's clashing with KMP artifacts naming scheme.
        //  In KMP JVM artifacts should have -jvm suffix
        //  but for pure JVM projects it is unexpected and unwanted
        val artifactId = jvmFragment.settings.publishing?.name ?: module.userReadableName
        val version = jvmFragment.settings.publishing?.version ?: error("version must be specified")

        val pomArtifact = DefaultArtifact(groupId, artifactId, "", "pom", version).setFile(
            generateFakePom(
                groupId,
                artifactId,
                version
            )
        )

        val artifacts = dependenciesResult.map { taskResult ->
            when (taskResult) {
                is JvmClassesJarTaskResult -> {
                    DefaultArtifact(
                        groupId,
                        artifactId,
                        "",
                        "jar",
                        version
                    ).setFile(taskResult.jarPath.toFile())
                }

                is SourcesJarTaskResult -> {
                    DefaultArtifact(
                        groupId,
                        artifactId,
                        "sources",
                        "jar",
                        version
                    ).setFile(taskResult.jarPath.toFile())
                }

                else -> error("Unsupported dependency result: ${taskResult.javaClass.name}")
            }
        } + pomArtifact

        return artifacts
    }

    private fun generateFakePom(groupId: String, artifactId: String, version: String): File {
        val model = Model()

        model.modelVersion = "4.0.0"

        model.groupId = groupId
        model.artifactId = artifactId
        model.version = version

        tempRoot.path.createDirectories()
        val tempPath = Files.createTempFile(tempRoot.path, "maven-deploy", ".pom")
        val tempFile = tempPath.toFile()
        tempFile.deleteOnExit()

        WriterFactory.newXmlWriter(tempFile).use { writer ->
            MavenXpp3Writer().write(writer, model)
        }

        return tempFile
    }

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