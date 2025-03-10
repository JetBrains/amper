/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.apache.maven.bridge.MavenRepositorySystem
import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.execution.MavenExecutionRequest
import org.apache.maven.internal.aether.DefaultRepositorySystemSessionFactory
import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.classworlds.ClassWorld
import org.codehaus.plexus.logging.AbstractLogger
import org.codehaus.plexus.logging.BaseLoggerManager
import org.codehaus.plexus.logging.Logger
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.deployment.DeployRequest
import org.eclipse.aether.installation.InstallRequest
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory
import org.eclipse.aether.repository.RemoteRepository
import org.slf4j.LoggerFactory
import java.nio.file.Path

internal fun createPlexusContainer(): PlexusContainer {
    val containerConfiguration = DefaultContainerConfiguration()
        .setClassWorld(ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader()))
        .setName("mavenCore")
        .setClassPathScanning(PlexusConstants.SCANNING_INDEX)
        .setAutoWiring(true)

    return DefaultPlexusContainer(containerConfiguration).also { it.loggerManager = Slf4jLoggerManager }
}

private object Slf4jLoggerManager : BaseLoggerManager() {
    init {
        threshold = Logger.LEVEL_DEBUG
    }

    override fun createLogger(name: String): Logger = object : AbstractLogger(LEVEL_DEBUG, name) {
        private val logger = LoggerFactory.getLogger(name)

        override fun debug(message: String, throwable: Throwable?) = logger.debug(message, throwable)
        override fun info(message: String, throwable: Throwable?) = logger.info(message, throwable)
        override fun warn(message: String, throwable: Throwable?) = logger.warn(message, throwable)
        override fun error(message: String, throwable: Throwable?) = logger.error(message, throwable)
        override fun fatalError(message: String, throwable: Throwable?) = logger.error(message, throwable)
        override fun getChildLogger(name: String): Logger = this
    }
}

internal fun PlexusContainer.deployToRemoteRepo(
    remoteRepository: RemoteRepository,
    localRepositoryPath: Path,
    artifacts: List<Artifact>,
) {
    val deployRequest = DeployRequest()
    deployRequest.repository = remoteRepository

    for (artifact in artifacts) {
        deployRequest.addArtifact(artifact)
    }

    val repositorySession = createRepositorySession(localRepositoryPath)
    repositorySystem.deploy(repositorySession, deployRequest)
}

internal fun PlexusContainer.installToMavenLocal(localRepositoryPath: Path, artifacts: List<Artifact>) {
    val installRequest = InstallRequest()
    for (artifact in artifacts) {
        installRequest.addArtifact(artifact)
    }
    val repositorySession = createRepositorySession(localRepositoryPath)
    repositorySystem.install(repositorySession, installRequest)
}

private fun PlexusContainer.createRepositorySession(localRepositoryPath: Path): DefaultRepositorySystemSession {
    val request = mavenRepositorySystem.createMavenExecutionRequest(localRepositoryPath)
    val session = repositorySystemSessionFactory.newRepositorySession(request)
    session.setConfigProperty(Maven2RepositoryLayoutFactory.CONFIG_PROP_CHECKSUMS_ALGORITHMS, "MD5,SHA-1,SHA-256,SHA-512")
    return session
}

private fun MavenRepositorySystem.createMavenExecutionRequest(localRepositoryPath: Path): MavenExecutionRequest {
    val request = DefaultMavenExecutionRequest()
    request.localRepository = createLocalRepository(request, localRepositoryPath.toFile())
    return request
}

private val PlexusContainer.repositorySystem: RepositorySystem
    get() = lookup(RepositorySystem::class.java)

private val PlexusContainer.mavenRepositorySystem: MavenRepositorySystem
    get() = lookup(MavenRepositorySystem::class.java)

private val PlexusContainer.repositorySystemSessionFactory: DefaultRepositorySystemSessionFactory
    get() = lookup(DefaultRepositorySystemSessionFactory::class.java)
