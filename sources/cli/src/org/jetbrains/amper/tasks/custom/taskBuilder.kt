/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import org.jetbrains.amper.frontend.CompositeStringPart
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.TaskGraphBuilderCtx

fun TaskGraphBuilderCtx.setupCustomTasks() {
    allModules().withEach {
        module.customTasks.forEach { description ->
            tasks.registerTask(
                CustomTask(
                    custom = description,
                    taskOutputRoot = context.getTaskOutputPath(description.name),
                    userCacheRoot = context.userCacheRoot,
                    terminal = context.terminal,
                    tempRoot = context.projectTempRoot,
                ),
                dependsOn = listOf(
                    CommonTaskType.RuntimeClasspath.getTaskName(description.customTaskCodeModule, Platform.JVM)
                ),
            )

            // explicit task dependencies
            description.dependsOn.forEach { dependsOn ->
                tasks.registerDependency(description.name, dependsOn)
            }

            // implicit task dependencies from references
            (description.environmentVariables.values + description.jvmArguments + description.programArguments).forEach { composite ->
                composite.parts.forEach { part ->
                    when (part) {
                        is CompositeStringPart.Literal -> Unit
                        is CompositeStringPart.CurrentTaskProperty -> Unit
                        is CompositeStringPart.ModulePropertyReference -> {
                            part.property.dependsOnModuleTask.forEach { dependsOn ->
                                tasks.registerDependency(
                                    description.name,
                                    dependsOn = TaskName.moduleTask(part.referencedModule, dependsOn),
                                )
                            }
                        }
                    }.let { } // exhaustive when
                }
            }
        }
    }
}
