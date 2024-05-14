/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.native.NativeCompileTask
import org.jetbrains.amper.util.BuildType

private fun isIosApp(platform: Platform, module: PotatoModule) =
    platform.isDescendantOf(Platform.IOS) && module.type.isApplication()

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
                terminal = context.terminal,
                isTest = false,
                compilationType = KotlinCompilationType.IOS_FRAMEWORK,
            ),
            ProjectTasksBuilder.Companion.CommonTaskType.Dependencies.getTaskName(module, platform, false)
        )

        val buildTaskName = IosTaskType.BuildIosApp.getTaskName(module, platform)
        registerTask(
            task = BuildAppleTask(
                platform = platform,
                module = module,
                buildType = buildType,
                executeOnChangedInputs = eoci,
                taskOutputPath = context.getTaskOutputPath(buildTaskName),
                terminal = context.terminal,
                taskName = buildTaskName,
                isTest = false,
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

    // TODO Handle tests.
    onCompileModuleDependency(Platform.IOS) { module, dependsOn, _, platform, isTest ->
        if (!isIosApp(platform, module)) return@onCompileModuleDependency
        if (isTest) return@onCompileModuleDependency

        registerDependency(
            IosTaskType.Framework.getTaskName(module, platform, false),
            CommonTaskType.Compile.getTaskName(dependsOn, platform, false)
        )
    }
}

internal enum class IosTaskType(override val prefix: String) : PlatformTaskType {
    Framework("framework"),
    BuildIosApp("buildIosApp"),
    RunIosApp("runIosApp"),
}
