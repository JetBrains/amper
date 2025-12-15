/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.contributor

import org.apache.maven.model.Dependency
import org.apache.maven.project.MavenProject
import org.jetbrains.amper.frontend.schema.DependencyScope
import org.jetbrains.amper.frontend.schema.ExternalMavenBomDependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.InternalDependency
import org.jetbrains.amper.frontend.schema.Module
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
                `object`<Module> {
                    Module::dependencies {
                        add(`object`<ExternalMavenBomDependency> {
                            ExternalMavenBomDependency::coordinates setTo scalar("${parent.groupId}:${parent.artifactId}:${parent.version}")
                        })
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
                `object`<Module> {
                    Module::dependencies {
                        if (localPath != null) {
                            add(`object`<InternalDependency> {
                                InternalDependency::path setTo scalar(localPath)
                                InternalDependency::exported setTo scalar(true)
                            })
                        } else {
                            add(`object`<ExternalMavenDependency> {
                                ExternalMavenDependency::coordinates setTo scalar("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                                ExternalMavenDependency::exported setTo scalar(true)
                            })
                        }
                    }
                }
            }
        }
        "runtime" -> {
            withDefaultContext {
                `object`<Module> {
                    Module::dependencies {
                        if (localPath != null) {
                            add(`object`<InternalDependency> {
                                InternalDependency::path setTo scalar(localPath)
                                InternalDependency::scope setTo scalar(DependencyScope.RUNTIME_ONLY)
                            })
                        } else {
                            add(`object`<ExternalMavenDependency> {
                                ExternalMavenDependency::coordinates setTo scalar("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                                ExternalMavenDependency::scope setTo scalar(DependencyScope.RUNTIME_ONLY)
                            })
                        }
                    }
                }
            }

            withTestContext {
                `object`<Module> {
                    Module::dependencies {
                        if (localPath != null) {
                            add(`object`<InternalDependency> {
                                InternalDependency::path setTo scalar(localPath)
                                InternalDependency::exported setTo scalar(true)
                            })
                        } else {
                            add(`object`<ExternalMavenDependency> {
                                ExternalMavenDependency::coordinates setTo scalar("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                                ExternalMavenDependency::exported setTo scalar(true)
                            })
                        }
                    }
                }
            }
        }
        "provided" -> {
            withDefaultContext {
                `object`<Module> {
                    Module::dependencies {
                        if (localPath != null) {
                            add(`object`<InternalDependency> {
                                InternalDependency::path setTo scalar(localPath)
                                InternalDependency::scope setTo scalar(DependencyScope.COMPILE_ONLY)
                            })
                        } else {
                            add(`object`<ExternalMavenDependency> {
                                ExternalMavenDependency::coordinates setTo scalar("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                                ExternalMavenDependency::scope setTo scalar(DependencyScope.COMPILE_ONLY)
                            })
                        }
                    }
                }
            }
        }
        "test" -> {
            withTestContext {
                `object`<Module> {
                    Module::dependencies {
                        if (localPath != null) {
                            add(`object`<InternalDependency> {
                                InternalDependency::path setTo scalar(localPath)
                            })
                        } else {
                            add(`object`<ExternalMavenDependency> {
                                ExternalMavenDependency::coordinates setTo scalar("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                            })
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
                `object`<Module> {
                    Module::dependencies {
                        add(`object`<ExternalMavenBomDependency> {
                            ExternalMavenBomDependency::coordinates setTo scalar("${dependency.groupId}:${dependency.artifactId}:${dependency.version}")
                        })
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