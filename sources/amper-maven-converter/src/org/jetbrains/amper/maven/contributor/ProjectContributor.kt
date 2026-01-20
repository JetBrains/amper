/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.schema.Project
import org.jetbrains.amper.maven.MavenPluginXml
import org.jetbrains.amper.maven.ProjectTreeBuilder

internal fun ProjectTreeBuilder.contributeProjects(reactorProjects: Set<MavenProject>) {
    project {
        `object`<Project> {
            if (reactorProjects.size != 1 || reactorProjects.first().basedir.toPath() != projectPath.parent) {
                Project::modules {
                    reactorProjects.filter { it.packaging != "pom" }.forEach { reactorProject ->
                        add(scalar(reactorProject.basedir.name))
                    }
                }
            }
        }
    }
}

internal fun ProjectTreeBuilder.contributeMavenPlugins(pluginXmls: List<MavenPluginXml>) {
    if (pluginXmls.isEmpty()) return

    project {
        `object`<Project> {
            Project::mavenPlugins {
                pluginXmls.forEach { pluginXml ->
                    val coordinates = "${pluginXml.groupId}:${pluginXml.artifactId}:${pluginXml.version}"
                    add(scalar(coordinates))
                }
            }
        }
    }
}
