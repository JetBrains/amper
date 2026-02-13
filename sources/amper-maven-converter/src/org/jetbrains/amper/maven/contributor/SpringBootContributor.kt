/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ModuleProduct
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.schema.SpringBootSettings
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
        `object`<Module> {
            Module::product {
                ModuleProduct::type setTo scalar(ProductType.JVM_APP)
            }
            Module::settings {
                Settings::springBoot {
                    SpringBootSettings::enabled setTo scalar(true)
                    SpringBootSettings::version setTo scalar(plugin.version)
                }

                val pluginConfig = plugin.configuration
                if (pluginConfig is Xpp3Dom) {
                    pluginConfig.children.forEach { child ->
                        if (child.name == "mainClass") {
                            if (child.value.startsWith("$")) {
                                reactorProject.properties[child.value.substring(2, child.value.length - 1)]?.let { propertyValue ->
                                    Settings::jvm {
                                        JvmSettings::mainClass setTo scalar(propertyValue.toString())
                                    }
                                }
                            } else {
                                Settings::jvm {
                                    JvmSettings::mainClass setTo scalar(child.value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}