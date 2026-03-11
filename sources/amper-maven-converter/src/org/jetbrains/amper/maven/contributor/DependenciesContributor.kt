/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.Dependency
import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.schema.DependencyScope
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.maven.ModuleBuilderBlock
import org.jetbrains.amper.maven.ProjectTreeBuilder
import org.jetbrains.amper.maven.YamlComment
import java.nio.file.Path
import javax.xml.stream.XMLStreamConstants.CDATA
import javax.xml.stream.XMLStreamConstants.CHARACTERS
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.XMLStreamReader
import kotlin.io.path.relativeTo

internal fun ProjectTreeBuilder.contributeDependencies(
    reactorProjects: Set<MavenProject>,
    referencedPomProjects: Set<MavenProject> = emptySet(),
) {
    val jarProjects = reactorProjects.filterJarProjects()
    val pomProjects = reactorProjects.filterPomProjects()

    val pomProjectsByCoordinates = pomProjects.associateBy {
        MavenCoordinates(it.groupId, it.artifactId, it.version)
    }

    val reactorCoordinates = reactorProjects.map { MavenCoordinates(it.groupId, it.artifactId, it.version) }.toSet()

    for (project in jarProjects) {
        module(project) {
            // Collect the full parent chain (direct parent → root of the entire hierarchy).
            // Deduplicate by coordinates, and skip reactor parents (they are local modules, not published BOMs).
            val seen = mutableSetOf<MavenCoordinates>()
            val ancestors = buildList {
                var current = project.parent
                while (current != null) {
                    val coords = MavenCoordinates(current.groupId, current.artifactId, current.version)
                    if (seen.add(coords)) add(current)
                    current = current.parent
                }
            }.filter { ancestor ->
                MavenCoordinates(ancestor.groupId, ancestor.artifactId, ancestor.version) !in reactorCoordinates
            }
            if (ancestors.isNotEmpty()) {
                withDefaultContext {
                    dependencies {
                        // Emit BOMs root-first so that nearer parent versions take precedence
                        for (ancestor in ancestors.reversed()) {
                            add(DeclarationOfExternalMavenBomDependency) {
                                coordinates("${ancestor.groupId}:${ancestor.artifactId}:${ancestor.version}")
                            }
                        }
                    }
                }
            }

            // Process each dependency
            project.dependencies.forEach { dependency ->
                if (dependency.type == "pom") {
                    handlePomDependency(dependency, project, pomProjectsByCoordinates)
                } else {
                    val localPath = findLocalModulePath(project.basedir.toPath(), dependency, jarProjects)
                    contributeDependency(dependency, localPath)
                }
            }
        }
    }

    for (pomProject in referencedPomProjects) {
        module(pomProject) {
            pomProject.dependencies.forEach { dependency ->
                contributeDependency(dependency, localPath = null)
            }
        }
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.addDependencyInContext(
    useTestContext: Boolean,
    dependency: Dependency,
    localPath: Path?,
    scope: DependencyScope? = null,
    exported: Boolean? = null,
) {
    val block: ModuleBuilderBlock = {
        dependencies {
            if (localPath != null) {
                add(DeclarationOfInternalDependency) {
                    path(localPath)
                    scope?.let { s -> scope(s) }
                    exported?.let { e -> exported(e) }
                }
            } else {
                add(DeclarationOfExternalMavenDependency) {
                    coordinates("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                    scope?.let { s -> scope(s) }
                    exported?.let { e -> exported(e) }
                }
            }
        }
    }

    if (useTestContext) {
        withTestContext(block)
    } else {
        withDefaultContext(block)
    }
}

private fun findLocalModulePath(
    from: Path,
    dependency: Dependency,
    jarProjects: Set<MavenProject>,
): Path? = jarProjects.find { it.matchesCoordinatesOf(dependency) }?.basedir?.toPath()?.relativeTo(from)

private fun MavenProject.matchesCoordinatesOf(dependency: Dependency): Boolean =
    groupId == dependency.groupId && artifactId == dependency.artifactId && version == dependency.version

private fun ProjectTreeBuilder.ModuleTreeBuilder.handlePomDependency(
    dependency: Dependency,
    currentProject: MavenProject,
    pomProjectsByCoordinates: Map<MavenCoordinates, MavenProject>,
) {
    val coordinates = MavenCoordinates(dependency.groupId, dependency.artifactId, dependency.version)
    val pomProject = pomProjectsByCoordinates[coordinates]

    if (pomProject != null) {
        val relativePath = pomProject.basedir.toPath().relativeTo(currentProject.basedir.toPath())
        contributeDependency(dependency, relativePath)
    } else {
        handleExternalPomDependency(dependency)
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.contributeDependency(
    dependency: Dependency,
    localPath: Path?,
) {
    when (dependency.scope) {
        "compile" -> {
            addDependencyInContext(useTestContext = false, dependency, localPath, exported = true)
        }
        "runtime" -> {
            addDependencyInContext(useTestContext = false, dependency, localPath, scope = DependencyScope.RUNTIME_ONLY)
        }
        "provided" -> {
            addDependencyInContext(useTestContext = false, dependency, localPath, scope = DependencyScope.COMPILE_ONLY)
        }
        "test" -> {
            addDependencyInContext(useTestContext = true, dependency, localPath)
        }
        "system" -> {
            // todo: report an error that produces the comment in yaml that we don't support local dependencies
        }
        "import" -> {
            withDefaultContext {
                dependencies {
                    add(DeclarationOfExternalMavenBomDependency) {
                        coordinates("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                    }
                }
            }
        }
        else -> {
            // todo: report an error that produces the comment in yaml that we don't support other scopes
        }
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.handleExternalPomDependency(
    dependency: Dependency,
) {
    addYamlComment(
        YamlComment(
            path = listOf("dependencies"),
            beforeKeyComment = null,
            afterValueComment = buildString {
                appendLine("WARNING: Amper does not support external POM dependencies with scopes different than import, manual configuration may be required.")
                append(createPomDependencyReferenceComment(dependency))
            },
        )
    )
}

private fun createPomDependencyReferenceComment(dependency: Dependency): String {
    val location = dependency.getLocation("")
    val locationInfo = if (location != null) {
        "${location.source.location}:${location.lineNumber}:${location.columnNumber}"
    } else ""

    // Try to extract the original XML from the source file
    val originalXml = location?.findElement { it.readFullElement() }

    return buildString {
        if (locationInfo.isNotEmpty()) {
            appendLine("Reference: $locationInfo")
        }
        if (originalXml != null) {
            append(originalXml)
        } else {
            appendLine("<dependency>")
            appendLine("  <groupId>${dependency.groupId}</groupId>")
            appendLine("  <artifactId>${dependency.artifactId}</artifactId>")
            appendLine("  <version>${dependency.version}</version>")
            appendLine("  <type>pom</type>")
            appendLine("  <scope>${dependency.scope ?: "compile"}</scope>")
            append("</dependency>")
        }
    }
}

private fun XMLStreamReader.readFullElement(): String = buildString {
    val elementName = localName

    append("<$elementName")
    for (i in 0 until attributeCount) {
        append(" ${getAttributeLocalName(i)}=\"${getAttributeValue(i)}\"")
    }
    append(">")

    var depth = 0
    var pendingText: String? = null

    while (hasNext()) {
        when (next()) {
            CHARACTERS, CDATA -> {
                val trimmedText = text.trim()
                if (trimmedText.isNotEmpty()) {
                    pendingText = trimmedText
                }
            }
            START_ELEMENT -> {
                pendingText = null
                appendLine()
                val indent = "  ".repeat(depth + 1)
                append("$indent<${localName}")
                for (i in 0 until attributeCount) {
                    append(" ${getAttributeLocalName(i)}=\"${getAttributeValue(i)}\"")
                }
                append(">")
                depth++
            }
            END_ELEMENT -> {
                if (depth == 0) {
                    appendLine()
                    append("</${localName}>")
                    break
                }
                if (pendingText != null) {
                    append("$pendingText</${localName}>")
                    pendingText = null
                } else {
                    appendLine()
                    val indent = "  ".repeat(depth)
                    append("$indent</${localName}>")
                }
                depth--
            }
        }
    }
}