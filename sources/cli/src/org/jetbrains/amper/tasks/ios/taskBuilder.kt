/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.native.NativeLinkTask
import org.jetbrains.amper.tasks.native.NativeTaskType
import org.jetbrains.amper.util.BuildType

/**
 * Set up apple-related tasks.
 */
fun ProjectTaskRegistrar.setupIosTasks() {
    onEachBuildType { module, eoci, platform, isTest, buildType ->
        val ctx = this@setupIosTasks.context

        fun configureTestTasks() {
            registerTask(
                task = IosKotlinTestTask(
                    taskName = CommonTaskType.Test.getTaskName(module, platform),
                    module = module,
                    projectRoot = ctx.projectRoot,
                    terminal = ctx.terminal,
                    platform = platform,
                ),
                dependsOn = NativeTaskType.Link.getTaskName(module, platform, isTest = true)
            )

            registerDependency(
                NativeTaskType.Link.getTaskName(module, platform, isTest = true),
                NativeTaskType.CompileKLib.getTaskName(module, platform, isTest = false),
            )
        }


        fun configureMainTasks() {
            val frameworkTaskName = IosTaskType.Framework.getTaskName(module, platform, false, buildType)
            val compileKLibTaskName = NativeTaskType.CompileKLib.getTaskName(module, platform, false)
            registerTask(
                task = NativeLinkTask(
                    module = module,
                    platform = platform,
                    userCacheRoot = ctx.userCacheRoot,
                    taskOutputRoot = ctx.getTaskOutputPath(frameworkTaskName),
                    executeOnChangedInputs = eoci,
                    taskName = frameworkTaskName,
                    tempRoot = ctx.projectTempRoot,
                    isTest = false,
                    compilationType = KotlinCompilationType.IOS_FRAMEWORK,
                    compileKLibTaskName = compileKLibTaskName,
                ),
                dependsOn = listOf(
                    compileKLibTaskName,
                    CommonTaskType.Dependencies.getTaskName(module, platform, false)
                )
            )

            val buildTaskName = IosTaskType.BuildIosApp.getTaskName(module, platform)
            registerTask(
                task = BuildAppleTask(
                    platform = platform,
                    module = module,
                    buildType = buildType,
                    executeOnChangedInputs = eoci,
                    taskOutputPath = ctx.getTaskOutputPath(buildTaskName),
                    taskName = buildTaskName,
                    isTest = false,
                ),
                dependsOn = listOf(frameworkTaskName)
            )

            val runTaskName = IosTaskType.RunIosApp.getTaskName(module, platform)
            registerTask(
                task = RunAppleTask(runTaskName, ctx.getTaskOutputPath(runTaskName)),
                dependsOn = listOf(buildTaskName)
            )
        }

        when {
            module.type != ProductType.IOS_APP -> return@onEachBuildType
            !platform.isDescendantOf(Platform.IOS) -> return@onEachBuildType
            isTest && buildType == BuildType.Release -> return@onEachBuildType
            isTest && buildType == BuildType.Debug -> configureTestTasks()
            buildType == BuildType.Release -> return@onEachBuildType
            else -> configureMainTasks()
        }
    }

    onRuntimeModuleDependency(Platform.IOS) { module, dependsOn, _, platform, isTest, buildType ->
        val configureLinkTask = when {
            module.type != ProductType.IOS_APP -> false
            !platform.isDescendantOf(Platform.IOS) -> false
            isTest && buildType == BuildType.Release -> false
            isTest && buildType == BuildType.Debug -> false /*configureTestTasks()*/
            buildType == BuildType.Release -> false
            else -> true
        }

        if (configureLinkTask) {
            registerDependency(
                IosTaskType.Framework.getTaskName(module, platform, false, buildType),
                NativeTaskType.CompileKLib.getTaskName(dependsOn, platform, false)
            )
        }
    }
}

internal enum class IosTaskType(override val prefix: String) : PlatformTaskType {
    Framework("framework"),
    BuildIosApp("buildIosApp"),
    RunIosApp("runIosApp"),
}
