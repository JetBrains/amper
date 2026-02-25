/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.ConfigurationContainer
import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.jetbrains.amper.frontend.schema.AllOpenPreset
import org.jetbrains.amper.frontend.schema.NoArgPreset
import org.jetbrains.amper.frontend.tree.ObjectBuilderContext
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.maven.ProjectTreeBuilder
import kotlin.io.path.div

internal fun ProjectTreeBuilder.contributeKotlinPlugin(reactorProjects: Set<MavenProject>) {
    for (project in reactorProjects.filterJarProjects()) {
        module(project.basedir.toPath() / "module.yaml") {
            project.model.build.plugins
                .filter { it.groupId == "org.jetbrains.kotlin" && it.artifactId == "kotlin-maven-plugin" }
                .forEach { plugin -> contributeKotlinPlugin(plugin) }
        }
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.contributeKotlinPlugin(
    plugin: Plugin,
) {
    // Kotlin plugin common settings
    withDefaultContext {
        settings {
            kotlin {
                version(plugin.version)
            }
        }
    }

    // executions
    plugin.executions.forEach { execution ->
        when (execution.id) {
            "compile" -> {
                withDefaultContext {
                    settings { configureKotlinExecution(execution) }
                }
            }
            "test-compile" -> {
                withTestContext {
                    settings { configureKotlinExecution(execution) }
                }
            }
        }
    }

    // top-level configuration
    if (plugin.configuration != null) {
        withDefaultContext {
            settings { configureKotlinExecution(plugin) }
        }
    }
}

private fun ObjectBuilderContext<DeclarationOfSettings>.configureKotlinExecution(container: ConfigurationContainer) {
    val config = container.configuration
    if (config is Xpp3Dom) {
        config.children.forEach { child ->
            when (child.name) {
                "args" -> {
                    val freeCompilerArgs = buildList {
                        child.children.forEach { arg ->
                            add(arg.value)
                        }
                    }
                    kotlin {
                        freeCompilerArgs {
                            freeCompilerArgs.forEach { arg ->
                                add(arg)
                            }
                        }
                    }
                }
                "compilerPlugins" -> {
                    child.children.forEach { plugin ->
                        when (plugin.value) {
                            "all-open" -> {
                                kotlin {
                                    allOpen {
                                        enabled(true)
                                    }
                                }
                            }
                            "no-arg" -> {
                                kotlin {
                                    noArg {
                                        enabled(true)
                                    }
                                }
                            }
                            "lombok" -> {
                                lombok {
                                    enabled(true)
                                }
                            }
                            "kotlinx-serialization" -> {
                                kotlin {
                                    serialization {
                                        enabled(true)
                                    }
                                }
                            }
                        }
                    }
                }
                "pluginOptions" -> {
                    child.children.forEach { option ->
                        if (option.value.startsWith("all-open:annotation=")) {
                            kotlin {
                                allOpen {
                                    annotations {
                                        add(option.value.substringAfter("all-open:annotation="))
                                    }
                                }
                            }
                        }
                        if (option.value.startsWith("all-open:preset=")) {
                            kotlin {
                                allOpen {
                                    presets {
                                        add(AllOpenPreset.valueOf(option.value.substringAfter("all-open:preset=")))
                                    }
                                }
                            }
                        }

                        if (option.value.startsWith("no-arg:annotation=")) {
                            kotlin {
                                noArg {
                                    annotations {
                                        add(option.value.substringAfter("no-arg:annotation="))
                                    }
                                }
                            }
                        }

                        if (option.value.startsWith("no-arg:preset=")) {
                            kotlin {
                                noArg {
                                    presets {
                                        add(NoArgPreset.valueOf(option.value.substringAfter("no-arg:preset=")))
                                    }
                                }
                            }
                        }
                    }
                }
                "jvmTarget" -> {
                    jvm {
                        release(child.value.toInt())
                    }
                }
                "javaParameters" -> {
                    jvm {
                        storeParameterNames(child.value.toBoolean())
                    }
                }
            }
        }
    }
}