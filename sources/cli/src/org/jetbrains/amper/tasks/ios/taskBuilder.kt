/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.native.NativeCompileTask
import org.jetbrains.amper.util.BuildType

/**
 * Setup apple related tasks.
 */
fun ProjectTaskRegistrar.setupIosTasks() {
    onEachBuildType { module, eoci, platform, isTest, buildType ->
        if (!platform.isDescendantOf(Platform.IOS)) return@onEachBuildType
        if (isTest || buildType == BuildType.Release) return@onEachBuildType

        val frameworkTaskName = IosTaskType.Framework.getTaskName(module, platform, false, buildType)
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

internal enum class IosTaskType(override val prefix: String) : PlatformTaskType {
    Framework("framework"),
    BuildIosApp("buildIosApp"),
    RunIosApp("runIosApp"),
}
