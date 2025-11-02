/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.fragmentsTargeting
import org.jetbrains.amper.frontend.plugins.generated.ShadowResolutionScope
import org.jetbrains.amper.frontend.plugins.generated.ShadowSourcesKind
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.getModuleDependencies

fun ProjectTasksBuilder.setupTasksFromPlugins() {
    allModules().withEach {
        module.tasksFromPlugins.forEach { taskDescription ->
            val taskDependencies = mutableListOf<TaskName>()
            for (sourcesRequest in taskDescription.requestedModuleSources) {
                val fragments = sourcesRequest.from.fragmentsTargeting(Platform.JVM, includeTestFragments = false)
                if (sourcesRequest.node.includeGenerated) {
                    val taskName = TaskName(taskDescription.backendTaskName.name + "*resolve-${sourcesRequest.propertyLocation}")
                    tasks.registerTask(
                        ModuleSourcesResolveTask(
                            taskName = taskName,
                            fragmentsForSources = fragments,
                            request = sourcesRequest,
                        )
                    )
                    taskDependencies.add(taskName)
                } else {
                    sourcesRequest.node.sourceDirectories =
                        sourcesRequest.from.fragmentsTargeting(Platform.JVM, includeTestFragments = false).flatMap {
                            when (sourcesRequest.node.kind) {
                                ShadowSourcesKind.KotlinJavaSources -> it.sourceRoots
                                ShadowSourcesKind.Resources -> listOf(it.resourcesPath)
                            }
                        }
                }
            }
            for (request in taskDescription.requestedCompilationArtifacts) {
                 taskDependencies += CommonTaskType.Jar.getTaskName(request.from, Platform.JVM)
            }
            taskDependencies += taskDescription.requestedClasspaths.map { classpathRequest ->
                val resolutionScope = when(classpathRequest.node.scope) {
                    ShadowResolutionScope.Runtime -> ResolutionScope.RUNTIME
                    ShadowResolutionScope.Compile -> ResolutionScope.COMPILE
                }
                val taskName = TaskName(taskDescription.backendTaskName.name + "*resolve-${classpathRequest.propertyLocation}")
                val task = ResolveClasspathRequestTask(
                    taskName = taskName,
                    classpathRequest = classpathRequest,
                )
                tasks.registerTask(
                    task,
                    dependsOn = buildList {
                        classpathRequest.localDependencies.forEach { module ->
                            add(CommonTaskType.Jar.getTaskName(module, Platform.JVM))
                            module.getModuleDependencies(
                                isTest = false,
                                platform = Platform.JVM,
                                dependencyReason = resolutionScope,
                                userCacheRoot = context.userCacheRoot,
                                incrementalCache = incrementalCache,
                            ).forEach {
                                add(CommonTaskType.Jar.getTaskName(it, Platform.JVM))
                            }
                        }

                        val resolveExternalTaskName = TaskName(taskName.name + "*external")
                        tasks.registerTask(ResolveCustomExternalDependenciesTask(
                            taskName = resolveExternalTaskName,
                            module = module,
                            incrementalCache = incrementalCache,
                            userCacheRoot = context.userCacheRoot,
                            resolutionScope = resolutionScope,
                            localDependencies = classpathRequest.localDependencies,
                            externalDependencies = classpathRequest.externalDependencies,
                        ))
                        add(resolveExternalTaskName)
                    }
                )
                taskName
            }
            val task = TaskFromPlugin(
                taskName = taskDescription.backendTaskName,
                module = module,
                description = taskDescription,
                buildOutputRoot = context.buildOutputRoot,
                terminal = context.terminal,
                incrementalCache = incrementalCache,
            )
            tasks.registerTask(
                task, dependsOn = buildList {
                    addAll(taskDependencies)
                    add(CommonTaskType.RuntimeClasspath.getTaskName(taskDescription.codeSource, Platform.JVM, isTest = false))
                    addAll(taskDescription.dependsOn.map { it.backendTaskName })
                }
            )
        }
    }
}
