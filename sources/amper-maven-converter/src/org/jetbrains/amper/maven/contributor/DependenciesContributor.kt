/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.Dependency
import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.schema.DependencyScope
import org.jetbrains.amper.frontend.tree.add
import org.jetbrains.amper.frontend.tree.invoke
import org.jetbrains.amper.frontend.types.generated.*
import org.jetbrains.amper.maven.ProjectTreeBuilder
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.relativeTo

internal fun ProjectTreeBuilder.contributeDependencies(reactorProjects: Set<MavenProject>) {
    val jarProjects = reactorProjects.filterJarProjects()

    for (project in jarProjects) {
        module(project.basedir.toPath() / "module.yaml") {
            // Get the first non-local parent for BOM import
            var parent = project.parent
            while (parent.basedir != null) {
                parent = parent.parent
            }

            withDefaultContext {
                dependencies {
                    add(DeclarationOfExternalMavenBomDependency) {
                        coordinates("${parent.groupId}:${parent.artifactId}:${parent.version}")
                    }
                }
            }

            // Process each dependency
            project.dependencies.forEach { dependency ->
                val localPath = findLocalModulePath(project.basedir.toPath(), dependency, jarProjects)
                contributeDependency(dependency, localPath)
            }
        }
    }
}

private fun ProjectTreeBuilder.ModuleTreeBuilder.contributeDependency(
    dependency: Dependency,
    localPath: Path?,
) {
    when (dependency.scope) {
        "compile" -> {
            withDefaultContext {
                dependencies {
                    if (localPath != null) {
                        add(DeclarationOfInternalDependency) {
                            path(localPath)
                            exported(true)
                        }
                    } else {
                        add(DeclarationOfExternalMavenDependency) {
                            coordinates("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                            exported(true)
                        }
                    }
                }
            }
        }
        "runtime" -> {
            withDefaultContext {
                dependencies {
                    if (localPath != null) {
                        add(DeclarationOfInternalDependency) {
                            path(localPath)
                            scope(DependencyScope.RUNTIME_ONLY)
                        }
                    } else {
                        add(DeclarationOfExternalMavenDependency) {
                            coordinates("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                            scope(DependencyScope.RUNTIME_ONLY)
                        }
                    }
                }
            }

            withTestContext {
                dependencies {
                    if (localPath != null) {
                        add(DeclarationOfInternalDependency) {
                            path(localPath)
                            exported(true)
                        }
                    } else {
                        add(DeclarationOfExternalMavenDependency) {
                            coordinates("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                            exported(true)
                        }
                    }
                }
            }
        }
        "provided" -> {
            withDefaultContext {
                dependencies {
                    if (localPath != null) {
                        add(DeclarationOfInternalDependency) {
                            path(localPath)
                            scope(DependencyScope.COMPILE_ONLY)
                        }
                    } else {
                        add(DeclarationOfExternalMavenDependency) {
                            coordinates("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                            scope(DependencyScope.COMPILE_ONLY)
                        }
                    }
                }
            }
        }
        "test" -> {
            withTestContext {
                dependencies {
                    if (localPath != null) {
                        add(DeclarationOfInternalDependency) {
                            path(localPath)
                        }
                    } else {
                        add(DeclarationOfExternalMavenDependency) {
                            coordinates("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                        }
                    }
                }
            }
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

private fun findLocalModulePath(
    from: Path,
    dependency: Dependency,
    jarProjects: Set<MavenProject>,
): Path? {
    val groupId = dependency.groupId
    val artifactId = dependency.artifactId
    val version = dependency.version

    for (reactiveProject in jarProjects) {
        if (reactiveProject.groupId == groupId && reactiveProject.artifactId == artifactId && reactiveProject.version == version) {
            return reactiveProject.basedir.toPath().relativeTo(from)
        }
    }
    return null
}