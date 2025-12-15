/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.maven.contributor.MavenRootNotFoundException
import org.jetbrains.amper.maven.contributor.contributeCompilerPlugin
import org.jetbrains.amper.maven.contributor.contributeCoreModule
import org.jetbrains.amper.maven.contributor.contributeDependencies
import org.jetbrains.amper.maven.contributor.contributeKotlinPlugin
import org.jetbrains.amper.maven.contributor.contributeProjects
import org.jetbrains.amper.maven.contributor.contributeRepositories
import org.jetbrains.amper.maven.contributor.contributeSpringBoot
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.div

/**
 * Converts a Maven project into the corresponding Amper project structure and materializes the result.
 *
 * It builds a plexus container (without plugin processing), makes a "build effective pom" request, and extracts the
 * maven model represented by container of [org.apache.maven.project.MavenProject]. After that it passes the data
 * structure and builds the Amper forest that corresponds to the maven model extracted on a previous step. And then
 * finally it materializes the forest to the file system.
 */
object MavenProjectConvertor {
    private val logger = LoggerFactory.getLogger(MavenProjectConvertor::class.java)

    /**
     * Converts a Maven project into the corresponding Amper project structure and materializes the result.
     *
     * @param pomXml Path to the Maven POM file from which reactor project discovery starts. pom.xml should be part of
     * the reactor project to be converted.
     * @param overwrite If true, existing files will be overwritten.
     */
    fun convert(pomXml: Path, overwrite: Boolean = false) {
        val reactorProjects = MavenModelReader.getReactorProjects(pomXml)

        val potentialRoots = potentialRoots(reactorProjects)

        if (potentialRoots.isEmpty()) {
            throw MavenRootNotFoundException(potentialRoots)
        }

        if (potentialRoots.size > 1) {
            logger.warn("Multiple maven project roots found:" +
                    "\n${potentialRoots.joinToString("\n") { "- ${it.basedir}" }}\n " +
                    "The first one will be taken, the rest will be ignored.")
        }

        val amperProjectPath = potentialRoots.first().basedir.toPath() / "project.yaml"

        val trees = amperProjectTreeBuilder(amperProjectPath) {
            contributeProjects(reactorProjects)
            contributeCoreModule(reactorProjects)
            contributeRepositories(reactorProjects)
            contributeDependencies(reactorProjects)
            contributeCompilerPlugin(reactorProjects)
            contributeKotlinPlugin(reactorProjects)
            contributeSpringBoot(reactorProjects)
        }.build()

        ProjectTreeMaterializer(trees).materialize(overwrite)
    }

    private fun potentialRoots(reactorProjects: Set<MavenProject>): Set<MavenProject> {
        val potentialRoots = buildSet {
            val queue = ArrayDeque(reactorProjects)
            while (queue.isNotEmpty()) {
                val project = queue.removeFirst()
                if (project.parent.basedir == null) {
                    add(project)
                    break
                } else {
                    queue.addLast(project.parent)
                }
            }
        }
        return potentialRoots
    }
}
