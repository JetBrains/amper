/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.InputLocation
import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.jetbrains.amper.maven.ProjectTreeBuilder
import org.slf4j.LoggerFactory
import java.io.IOException
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream

private val logger = LoggerFactory.getLogger("ContributorUtils")

class MavenRootNotFoundException(potentialRoots: Set<MavenProject>) :
    Exception("No root maven module found: ${potentialRoots.joinToString(", ")}")

internal fun Set<MavenProject>.filterJarProjects(): Set<MavenProject> = filter { it.packaging == "jar" }.toSet()

internal fun Set<MavenProject>.filterPomProjects(): Set<MavenProject> = filter { it.packaging == "pom" }.toSet()

internal fun MavenProject.getEffectivePlugin(groupId: String, artifactId: String): Plugin? {
    val explicitPlugin = model.build?.plugins?.firstOrNull {
        it.groupId == groupId && it.artifactId == artifactId
    }
    if (explicitPlugin != null) return explicitPlugin

    return model.build?.pluginManagement?.plugins?.firstOrNull {
        it.groupId == groupId && it.artifactId == artifactId
    }
}

internal fun <T> InputLocation.findElement(handler: (XMLStreamReader) -> T): T? {
    val sourcePath = source?.location ?: return null
    val path = Path(sourcePath)
    if (!path.exists()) return null

    val targetLine = lineNumber
    val targetColumn = columnNumber

    try {
        val factory = XMLInputFactory.newInstance()
        path.inputStream().use { inputStream ->
            val reader = factory.createXMLStreamReader(inputStream)
            while (reader.hasNext()) {
                val event = reader.next()
                if (event == START_ELEMENT) {
                    val location = reader.location
                    if (location.lineNumber == targetLine) {
                        val elementNameLength = reader.localName.length
                        val expectedMavenColumn = location.columnNumber + elementNameLength + 2
                        if (expectedMavenColumn == targetColumn) {
                            return handler(reader)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        when (e) {
            is IOException, is XMLStreamException ->
                logger.warn("Failed to read element from $sourcePath at line $targetLine, column $targetColumn", e)
            else -> throw e
        }
    }

    return null
}


internal fun collectReferencedPomProjects(reactorProjects: Set<MavenProject>): Set<MavenProject> {
    val jarProjects = reactorProjects.filterJarProjects()
    val pomProjects = reactorProjects.filterPomProjects()
    val pomProjectsByCoordinates = pomProjects.associateBy {
        MavenCoordinates(it.groupId, it.artifactId, it.version)
    }

    return buildSet {
        for (project in jarProjects) {
            for (dependency in project.dependencies) {
                if (dependency.type == "pom") {
                    val coords = MavenCoordinates(dependency.groupId, dependency.artifactId, dependency.version)
                    pomProjectsByCoordinates[coords]?.let { add(it) }
                }
            }
        }
    }
}

internal data class MavenCoordinates(
    val groupId: String,
    val artifactId: String,
    val version: String,
)

internal inline fun ProjectTreeBuilder.module(
    project: MavenProject,
    block: ProjectTreeBuilder.ModuleTreeBuilder.() -> Unit
) {
    module(project.basedir.toPath() / "module.yaml", block)
}
