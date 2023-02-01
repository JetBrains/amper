/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.resolver

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.apache.maven.model.building.ModelSource2
import org.apache.maven.project.DefaultProjectBuildingRequest
import org.apache.maven.project.ProjectBuilder
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.codehaus.plexus.DefaultContainerConfiguration
import org.codehaus.plexus.DefaultPlexusContainer
import org.codehaus.plexus.PlexusConstants
import org.codehaus.plexus.PlexusContainer
import org.codehaus.plexus.classworlds.ClassWorld
import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.logging.console.ConsoleLoggerManager
import org.eclipse.aether.DefaultRepositoryCache
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RepositoryPolicy
import org.eclipse.aether.resolution.ResolutionErrorPolicy
import org.eclipse.aether.transfer.TransferEvent
import org.eclipse.aether.transfer.TransferListener
import org.eclipse.aether.util.repository.SimpleResolutionErrorPolicy
import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.use
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Path
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.createDirectories

class MavenResolver(private val userCacheRoot: AmperUserCacheRoot) {
    companion object {
        private val mavenContainer: PlexusContainer by lazy {
            val containerConfiguration = DefaultContainerConfiguration()
                .setClassWorld(ClassWorld("plexus.core", Thread.currentThread().getContextClassLoader()))
                .setName("mavenCore")
                .setClassPathScanning(PlexusConstants.SCANNING_INDEX)
                .setAutoWiring(true)

            val loggerManager = ConsoleLoggerManager()
                .also { it.threshold = Logger.LEVEL_WARN }

            return@lazy DefaultPlexusContainer(containerConfiguration)
                .also { it.loggerManager = loggerManager }
        }
    }

    fun resolve(coordinates: List<String>): List<Path> = spanBuilder("mavenResolve")
        .setAttribute("coordinates", coordinates.joinToString(" "))
        .startSpan().use { span ->
            val projectBuilder = mavenContainer.lookup(ProjectBuilder::class.java)

            val session = MavenRepositorySystemUtils.newSession()

            // Disable transfer errors caching to force re-request missing artifacts and metadata on network failures.
            // Despite this, some errors are still cached in session data, and for proper retries work we must reset this data after failure
            // what's performed by retryWithClearSessionData()
            val artifactCachePolicy = ResolutionErrorPolicy.CACHE_NOT_FOUND
            val metadataCachePolicy = ResolutionErrorPolicy.CACHE_NOT_FOUND

            session.setResolutionErrorPolicy(SimpleResolutionErrorPolicy(artifactCachePolicy, metadataCachePolicy))
            session.setCache(DefaultRepositoryCache())
            session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_NEVER)

            val logger = LoggerFactory.getLogger(javaClass)
            session.transferListener = object : TransferListener {
                override fun transferInitiated(event: TransferEvent?) {
                    logger.debug("Transfer Initiated: {}", event)
                }

                override fun transferStarted(event: TransferEvent?) {
                    logger.debug("Transfer Started: {}", event)
                }

                override fun transferProgressed(event: TransferEvent?) {
                    logger.trace("Transfer Progressed: {}", event)
                }

                override fun transferCorrupted(event: TransferEvent?) {
                    logger.error("Transfer Corrupted: $event", event?.exception)
                }

                override fun transferSucceeded(event: TransferEvent?) {
                    logger.info("Transfer Done: $event")
                }

                override fun transferFailed(event: TransferEvent?) {
                    logger.debug("Transfer Failed: $event", event?.exception)
                }
            }

            // in parallel
            session.setSystemProperty("aether.dependencyCollector.impl", "bf")
            // it's buggy
            session.setSystemProperty("aether.connector.resumeDownloads", "false")
            // it's fair
            session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL)

            // keep it isolated
            val localRepo = userCacheRoot.path.resolve(".m2.cache").also { it.createDirectories() }
            session.setLocalRepositoryManager(
                SimpleLocalRepositoryManagerFactory().newInstance(
                    session,
                    LocalRepository(localRepo.toFile())
                )
            )

            val projectBuildingRequest = DefaultProjectBuildingRequest()
            projectBuildingRequest.setRepositorySession(session)
            projectBuildingRequest.setResolveDependencies(true)

            val pom = getStubPom(coordinates, span)
            val result = projectBuilder.build(object : ModelSource2 {
                override fun getInputStream(): InputStream = ByteArrayInputStream(pom)
                override fun getLocation(): String = "memory"
                override fun getRelatedSource(relPath: String?): ModelSource2? = null
                override fun getLocationURI(): URI = URI("none")
            }, projectBuildingRequest)

            val errors = mutableListOf<Throwable>()
            for (problem in result.problems) {
                errors.add(IllegalStateException("MavenResolve: ${problem.message}", problem.exception))
            }
            errors.addAll(result.dependencyResolutionResult.collectionErrors)

            for (unresolvedDependency in result.dependencyResolutionResult.unresolvedDependencies) {
                val resolutionErrors = result.dependencyResolutionResult.getResolutionErrors(unresolvedDependency)

                if (resolutionErrors.isNotEmpty()) {
                    errors.addAll(resolutionErrors)
                } else {
                    errors.add(IllegalStateException("Unable to resolve maven dependency: $unresolvedDependency"))
                }
            }

            if (errors.size > 0) {
                val first = errors.first()
                errors.drop(1).forEach { first.addSuppressed(it) }
                throw first
            }

            val files = result.dependencyResolutionResult.dependencies.mapNotNull { it.artifact?.file?.toPath() }
            return files
        }

    private fun getStubPom(coordinates: List<String>, span: Span): ByteArray {
        val documentBuilder = createDocumentBuilder()
        val doc = documentBuilder.parse(
            ByteArrayInputStream(
                """
            <project xmlns="http://maven.apache.org/POM/4.0.0" 
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              
              <groupId>stub.groupId</groupId>
              <artifactId>stub.artifactId</artifactId>
              <version>1.0.0</version>
                
            </project>
        """.trimIndent().toByteArray()
            )
        )

        fun Element.appendElement(tagName: String): Element {
            val element = doc.createElement(tagName)
            appendChild(element)
            return element
        }

        val dependencies = doc.documentElement.appendElement("dependencies")

        for (coordinate in coordinates) {
            val split = coordinate.split(':')
            if (split.size != 3) {
                error("Invalid maven coordinates: $coordinate")
            }

            val dependency = dependencies.appendElement("dependency")

            dependency.appendElement("groupId").textContent = split[0]
            dependency.appendElement("artifactId").textContent = split[1]
            dependency.appendElement("version").textContent = split[2]
        }

        val byteArrayOutputStream = ByteArrayOutputStream()

        val transformerFactory = TransformerFactory.newInstance()
        val transformer = transformerFactory.newTransformer()
        transformer.transform(DOMSource(doc), StreamResult(byteArrayOutputStream))

        val byteArray = byteArrayOutputStream.toByteArray()

        span.addEvent(
            "pomGenerated",
            Attributes.builder()
                .put("coordinates", coordinates.joinToString(" "))
                .put("generatedPom", byteArray.decodeToString())
                .build()
        )

        return byteArray
    }
}
