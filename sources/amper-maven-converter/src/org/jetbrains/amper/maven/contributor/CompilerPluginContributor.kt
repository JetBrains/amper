/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.ConfigurationContainer
import org.apache.maven.model.Plugin
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.jetbrains.amper.frontend.tree.ObjectBuilderContext
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.types.generated.*
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
                    settings {
                        configureCompilerExecution(execution)
                    }
                }
            }
        }
    }

    if (plugin.configuration != null) {
        withDefaultContext {
            settings {
                configureCompilerExecution(plugin)
            }
        }
    }
}

private fun ObjectBuilderContext<DeclarationOfSettings>.configureCompilerExecution(container: ConfigurationContainer) {
    val config = container.configuration
    if (config is Xpp3Dom) {
        config.children.filterNotNull().forEach { child ->
            when (child.name) {
                "compilerArgs" -> {
                    java {
                        freeCompilerArgs {
                            child.children.forEach { arg -> add(arg.value) }
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
                        java {
                            annotationProcessing {
                                processors {
                                    add(DeclarationOfUnscopedExternalMavenDependency) {
                                        coordinates(annotationProcessorCoordinates)
                                    }
                                }
                            }
                        }
                    }
                }
                "parameters" -> {
                    jvm {
                        storeParameterNames(child.value.toBoolean())
                    }
                }
                "release" -> {
                    jvm {
                        release(child.value.toInt())
                    }
                }
            }
        }
    }
}