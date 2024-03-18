/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isParent
import org.jetbrains.amper.tasks.NativeCompileTask
import org.jetbrains.amper.tasks.OnBuildTypePrecondition
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.TaskType


val isIosFamily: OnBuildTypePrecondition = { _, platform, _, _ -> platform.isParent(Platform.IOS) }

/**
 * Setup apple related tasks.
 */
fun ProjectTaskRegistrar.setupIosTasks() {
    onBuildType(isIosFamily) { module, eoci, platform, _, buildType ->
        val frameworkTaskName = IosTaskType.Framework.getTaskName(module, platform)
        registerTask(
            NativeCompileTask(
                module = module,
                platform = platform,
                userCacheRoot = context.userCacheRoot,
                taskOutputRoot = context.getTaskOutputPath(frameworkTaskName),
                executeOnChangedInputs = eoci,
                taskName = frameworkTaskName,
                tempRoot = context.projectTempRoot,
                isTest = false,
                isFramework = true,
            ),
            ProjectTasksBuilder.Companion.CommonTaskType.Dependencies.getTaskName(module, platform, false)
        )

        val buildTaskName = IosTaskType.BuildIosApp.getTaskName(module, platform)
        registerTask(
            task = BuildAppleTask(
                platform,
                module,
                buildType,
                eoci,
                context.getTaskOutputPath(buildTaskName),
                buildTaskName,
            ),
            dependsOn = listOf(frameworkTaskName)
        )

        val runTaskName = IosTaskType.RunIosApp.getTaskName(module, platform)
        registerTask(
            task = RunAppleTask(
                runTaskName,
                context.getTaskOutputPath(runTaskName),
            ),
            dependsOn = listOf(buildTaskName)
        )
    }
}

internal enum class IosTaskType(override val prefix: String) : TaskType {
    Framework("framework"),
    BuildIosApp("buildIosApp"),
    RunIosApp("runIosApp"),
}
