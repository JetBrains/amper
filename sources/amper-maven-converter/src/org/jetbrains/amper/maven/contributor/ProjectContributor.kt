/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.maven.MavenPluginXml
import org.jetbrains.amper.maven.ProjectTreeBuilder

internal fun ProjectTreeBuilder.contributeProjects(reactorProjects: Set<MavenProject>) {
    project {
        if (reactorProjects.size != 1 || reactorProjects.first().basedir.toPath() != projectPath.parent) {
            modules {
                reactorProjects.filter { it.packaging != "pom" }.forEach { reactorProject ->
                    add(reactorProject.basedir.name)
                }
            }
        }
    }
}

internal fun ProjectTreeBuilder.contributeProjectMavenPlugins(pluginXmls: List<MavenPluginXml>) {
    if (pluginXmls.isEmpty()) return

    project {
        mavenPlugins {
            pluginXmls.forEach { pluginXml ->
                add {
                    coordinates("${pluginXml.groupId}:${pluginXml.artifactId}:${pluginXml.version}")
                }
            }
        }
    }
}
