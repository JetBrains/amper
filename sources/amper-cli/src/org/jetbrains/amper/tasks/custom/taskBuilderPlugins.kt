/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.fragmentsTargeting
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.getModuleDependencies

fun ProjectTasksBuilder.setupTasksFromPlugins() {
    allModules().withEach {
        module.tasksFromPlugins.forEach { taskDescription ->
            for (sourcesRequest in taskDescription.requestedModuleSources) {
                sourcesRequest.node.sourceDirectories =
                    sourcesRequest.from.fragmentsTargeting(Platform.JVM, includeTestFragments = false).map { it.src }
            }
            val classpathRequestTasks = taskDescription.requestedClasspaths.map { classpathRequest ->
                val taskName = TaskName(taskDescription.name.name + "*resolve-${classpathRequest.propertyLocation}")
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
                                Platform.JVM,
                                ResolutionScope.RUNTIME,
                                context.userCacheRoot,
                            ).forEach {
                                // FIXME: Get those in the TaskFromPlugin task
                                add(CommonTaskType.Jar.getTaskName(it, Platform.JVM))
                            }
                        }

                        val resolveExternalTaskName = TaskName(taskName.name + "*external")
                        tasks.registerTask(ResolveCustomExternalDependenciesTask(
                            taskName = resolveExternalTaskName,
                            module = module,
                            incrementalCache = incrementalCache,
                            userCacheRoot = context.userCacheRoot,
                            resolutionScope = ResolutionScope.RUNTIME,
                            localDependencies = classpathRequest.localDependencies,
                            externalDependencies = classpathRequest.externalDependencies,
                        ))
                        add(resolveExternalTaskName)
                    }
                )
                taskName
            }
            val task = TaskFromPlugin(
                taskName = taskDescription.name,
                module = module,
                description = taskDescription,
                buildOutputRoot = context.buildOutputRoot,
                terminal = context.terminal,
                incrementalCache = incrementalCache,
            )
            tasks.registerTask(
                task, dependsOn = buildList {
                    addAll(classpathRequestTasks)
                    // TODO: Take explicit dependencies into account
                    add(CommonTaskType.RuntimeClasspath.getTaskName(taskDescription.codeSource, Platform.JVM, isTest = false))
                }
            )
        }
    }
}
