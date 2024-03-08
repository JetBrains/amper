/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.NativeCompileTask
import org.jetbrains.amper.tasks.NativeRunTask
import org.jetbrains.amper.tasks.NativeTestTask
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath

fun ProjectTaskRegistrar.setupNativeTasks() {
    onTaskType(Platform.NATIVE) { module, executeOnChangedInputs, platform, isTest ->
        val compileTaskName = CommonTaskType.Compile.getTaskName(module, platform, isTest)
        registerTask(
            NativeCompileTask(
                module = module,
                platform = platform,
                userCacheRoot = context.userCacheRoot,
                taskOutputRoot = context.getTaskOutputPath(compileTaskName),
                executeOnChangedInputs = executeOnChangedInputs,
                taskName = compileTaskName,
                tempRoot = context.projectTempRoot,
                isTest = isTest,
            ),
            CommonTaskType.Dependencies.getTaskName(module, platform, isTest)
        )
    }

    onMain(Platform.NATIVE) { module, _, platform, isTest ->
        val runTaskName = CommonTaskType.Run.getTaskName(module, platform)
        registerTask(
            NativeRunTask(
                module = module,
                projectRoot = context.projectRoot,
                taskName = runTaskName,
                platform = platform,
                commonRunSettings = context.commonRunSettings,
            ),
            CommonTaskType.Compile.getTaskName(module, platform, isTest)
        )
    }

    onTest(Platform.NATIVE) { module, _, platform, isTest ->
        val testTaskName = CommonTaskType.Test.getTaskName(module, platform)
        registerTask(
            NativeTestTask(
                module = module,
                projectRoot = context.projectRoot,
                taskName = testTaskName,
                platform = platform,
            ),
            CommonTaskType.Compile.getTaskName(module, platform, isTest)
        )
        registerDependency(
            taskName = CommonTaskType.Compile.getTaskName(module, platform, true),
            dependsOn = CommonTaskType.Compile.getTaskName(module, platform, false),
        )
    }
}