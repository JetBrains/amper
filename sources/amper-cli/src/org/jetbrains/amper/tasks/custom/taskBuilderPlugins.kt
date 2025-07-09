/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder

fun ProjectTasksBuilder.setupTasksFromPlugins() {
    allModules().withEach {
        module.tasksFromPlugins.forEach { taskDescription ->
            val task = TaskFromPlugin(
                taskName = taskDescription.name,
                module = module,
                description = taskDescription,
                buildOutputRoot = context.buildOutputRoot,
                terminal = context.terminal,
            )
            tasks.registerTask(
                task, dependsOn = listOf(
                    // TODO: Take explicit dependencies into account
                    CommonTaskType.RuntimeClasspath.getTaskName(taskDescription.codeSource, Platform.JVM, isTest = false),
                )
            )
        }
    }
}

