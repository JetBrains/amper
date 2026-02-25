/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.maven.ProjectTreeBuilder
import kotlin.io.path.div

internal fun ProjectTreeBuilder.contributeSpringBootPlugin(reactorProjects: Set<MavenProject>) {
    for (project in reactorProjects.filterJarProjects()) {
        module(project.basedir.toPath() / "module.yaml") {
            project.model.build.plugins
                .filter { it.groupId == "org.springframework.boot" && it.artifactId == "spring-boot-maven-plugin" }
                .forEach { plugin -> contributeSpringBootPlugin(project, plugin) }
        }
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.contributeSpringBootPlugin(
    reactorProject: MavenProject,
    plugin: Plugin,
) {
    withDefaultContext {
        product {
            type(ProductType.JVM_APP)
        }
        settings {
            springBoot {
                enabled(true)
                version(plugin.version)
            }

            val pluginConfig = plugin.configuration
            if (pluginConfig is Xpp3Dom) {
                pluginConfig.children.forEach { child ->
                    if (child.name == "mainClass") {
                        if (child.value.startsWith("$")) {
                            reactorProject.properties[child.value.substring(2, child.value.length - 1)]?.let { propertyValue ->
                                jvm {
                                    mainClass(propertyValue.toString())
                                }
                            }
                        } else {
                            jvm {
                                mainClass(child.value)
                            }
                        }
                    }
                }
            }
        }
    }
}