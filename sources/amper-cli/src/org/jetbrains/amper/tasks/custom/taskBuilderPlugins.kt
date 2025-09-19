/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.fragmentsTargeting
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder

fun ProjectTasksBuilder.setupTasksFromPlugins() {
    allModules().withEach {
        module.tasksFromPlugins.forEach { taskDescription ->
            for (sourcesRequest in taskDescription.requestedModuleSources) {
                sourcesRequest.node.sourceDirectories =
                    sourcesRequest.from.fragmentsTargeting(Platform.JVM, includeTestFragments = false).map { it.src }
            }
            val runtimeClasspathTasks = taskDescription.requestedClasspaths.flatMap {
                it.localDependencies.map { module ->
                    CommonTaskType.RuntimeClasspath.getTaskName(module, Platform.JVM)
                }
            }
            val customResolveTasks = taskDescription.requestedClasspaths.filter {
                it.externalDependencies.isNotEmpty()
            }.mapIndexed { index, request ->
                val name = TaskName(taskDescription.name.name + "*resolve$index")
                tasks.registerTask(ResolveCustomExternalDependenciesTask(
                    taskName = name,
                    destination = request,
                    module = module,
                    incrementalCache = executeOnChangedInputs,
                    userCacheRoot = context.userCacheRoot,
                ))
                name
            }
            val task = TaskFromPlugin(
                taskName = taskDescription.name,
                module = module,
                description = taskDescription,
                buildOutputRoot = context.buildOutputRoot,
                terminal = context.terminal,
                incrementalCache = executeOnChangedInputs,
            )
            tasks.registerTask(
                task, dependsOn = buildList {
                    addAll(runtimeClasspathTasks)
                    addAll(customResolveTasks)
                    // TODO: Take explicit dependencies into account
                    add(CommonTaskType.RuntimeClasspath.getTaskName(taskDescription.codeSource, Platform.JVM, isTest = false))
                }
            )
        }
    }
}
