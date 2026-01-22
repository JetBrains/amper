/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.ConfigurationContainer
import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.jetbrains.amper.frontend.schema.JavaAnnotationProcessingSettings
import org.jetbrains.amper.frontend.schema.JavaSettings
import org.jetbrains.amper.frontend.schema.JvmSettings
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.frontend.tree.SyntheticBuilder
import org.jetbrains.amper.frontend.types.SchemaType
import org.jetbrains.amper.maven.ProjectTreeBuilder
import kotlin.io.path.div


internal fun ProjectTreeBuilder.contributeCompilerPlugin(reactorProjects: Set<MavenProject>) {
    for (project in reactorProjects.filterJarProjects()) {
        module(project.basedir.toPath() / "module.yaml") {
            val compilerPlugin = project.getEffectivePlugin(
                "org.apache.maven.plugins",
                "maven-compiler-plugin"
            )
            compilerPlugin?.let { contributeCompilerPlugin(it) }
        }
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.contributeCompilerPlugin(plugin: Plugin) {
    plugin.executions.forEach { execution ->
        when (execution.id) {
            "default-compile" -> {
                withDefaultContext {
                    `object`<Module> {
                        Module::settings { execution.configureCompilerExecution() }
                    }
                }
            }
        }
    }

    if (plugin.configuration != null) {
        withDefaultContext {
            `object`<Module> {
                Module::settings { plugin.configureCompilerExecution() }
            }
        }
    }
}

context(sb: SyntheticBuilder, mapLikeValueBuilder: SyntheticBuilder.MapLikeValueBuilder)
private fun ConfigurationContainer.configureCompilerExecution() {
    with(mapLikeValueBuilder) {
        val config = configuration
        if (config is Xpp3Dom) {
            config.children.filterNotNull().forEach { child ->
                when (child.name) {
                    "compilerArgs" -> {
                        val freeCompilerArgs = buildList { child.children.forEach { arg -> add(arg.value) } }
                        Settings::java {
                            JavaSettings::freeCompilerArgs setTo sb.list(SchemaType.ListType(SchemaType.StringType)) {
                                freeCompilerArgs.forEach { add(sb.scalar(it)) }
                            }
                        }
                    }
                    "annotationProcessorPaths" -> {
                        child.children.forEach { annotationProcessorPath ->
                            val annotationProcessorCoordinates = buildString {
                                if (annotationProcessorPath is Xpp3Dom) {
                                    when (annotationProcessorPath.name) {
                                        "path" -> {
                                            annotationProcessorPath.children.forEach { path ->
                                                if (path is Xpp3Dom) {
                                                    when (path.name) {
                                                        "groupId" -> append(path.value)
                                                        "artifactId" -> append(":${path.value}")
                                                        "version" -> append(":${path.value}")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Settings::java {
                                JavaSettings::annotationProcessing {
                                    JavaAnnotationProcessingSettings::processors {
                                        this += sb.scalar(annotationProcessorCoordinates)
                                    }
                                }
                            }
                        }
                    }
                    "parameters" -> {
                        Settings::jvm {
                            JvmSettings::storeParameterNames setTo sb.scalar(child.value.toBoolean())
                        }
                    }
                    "release" -> {
                        Settings::jvm {
                            JvmSettings::release setTo sb.scalar(child.value)
                        }
                    }
                }
            }
        }
    }
}