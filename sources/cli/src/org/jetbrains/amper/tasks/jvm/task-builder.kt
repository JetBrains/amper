/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.JvmCompileTask
import org.jetbrains.amper.tasks.JvmRunTask
import org.jetbrains.amper.tasks.JvmTestTask
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath

fun ProjectTaskRegistrar.setupJvmTasks() {
    onTaskType(Platform.JVM) { module, executeOnChangedInputs, platform, isTest ->
        val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
        val compileTaskName = CommonTaskType.Compile.getTaskName(module, platform, isTest)
        registerTask(
            JvmCompileTask(
                module = module,
                isTest = isTest,
                fragments = fragments,
                userCacheRoot = context.userCacheRoot,
                projectRoot = context.projectRoot,
                taskOutputRoot = context.getTaskOutputPath(compileTaskName),
                taskName = compileTaskName,
                executeOnChangedInputs = executeOnChangedInputs,
            ),
            CommonTaskType.Dependencies.getTaskName(module, platform, isTest)
        )
    }

    onCompileModuleDependency(Platform.JVM) { module, dependsOn, _, platform, isTest ->
        registerDependency(
            CommonTaskType.Compile.getTaskName(module, platform, isTest),
            CommonTaskType.Compile.getTaskName(dependsOn, platform, false)
        )
    }

    onMain(Platform.JVM) { module, _, platform, _ ->
        if (!module.type.isLibrary()) {
            registerTask(
                JvmRunTask(
                    module = module,
                    userCacheRoot = context.userCacheRoot,
                    projectRoot = context.projectRoot,
                    taskName = CommonTaskType.Run.getTaskName(module, platform),
                    commonRunSettings = context.commonRunSettings,
                ),
                CommonTaskType.Compile.getTaskName(module, platform)
            )
        }
    }

    onTest(Platform.JVM) { module, _, platform, _ ->
        val testTaskName = CommonTaskType.Test.getTaskName(module, platform)
        registerTask(
            JvmTestTask(
                module = module,
                userCacheRoot = context.userCacheRoot,
                projectRoot = context.projectRoot,
                taskName = testTaskName,
                taskOutputRoot = context.getTaskOutputPath(testTaskName),
            ),
            listOf(
                CommonTaskType.Compile.getTaskName(module, platform, true),
            )
        )

        registerDependency(
            taskName = CommonTaskType.Compile.getTaskName(module, platform, true),
            dependsOn = CommonTaskType.Compile.getTaskName(module, platform, false),
        )
    }
}
