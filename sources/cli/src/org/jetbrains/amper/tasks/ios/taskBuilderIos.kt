/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.native.NativeTaskType
import org.jetbrains.amper.util.BuildType

/**
 * Set up apple-related tasks.
 */
fun ProjectTasksBuilder.setupIosTasks() {
    allModules()
        .alsoPlatforms(Platform.IOS)
        .alsoTests()
        .filterModuleType { it == ProductType.IOS_APP }
        .withEach {
            val ctx = this@setupIosTasks.context

            fun configureTestTasks() {
                tasks.registerTask(
                    task = IosKotlinTestTask(
                        taskName = CommonTaskType.Test.getTaskName(module, platform),
                        module = module,
                        projectRoot = ctx.projectRoot,
                        terminal = ctx.terminal,
                        platform = platform,
                    ),
                    dependsOn = NativeTaskType.Link.getTaskName(module, platform, isTest = true)
                )
            }

            fun configureMainTasks() {
                val buildTaskName = IosTaskType.BuildIosApp.getTaskName(module, platform)
                tasks.registerTask(
                    task = BuildAppleTask(
                        platform = platform,
                        module = module,
                        buildType = BuildType.Debug,
                        executeOnChangedInputs = executeOnChangedInputs,
                        taskOutputPath = ctx.getTaskOutputPath(buildTaskName),
                        taskName = buildTaskName,
                        isTest = false,
                    ),
                    dependsOn = listOf(
                        IosTaskType.Framework.getTaskName(module, platform, false, BuildType.Debug)
                    )
                )

                val runTaskName = IosTaskType.RunIosApp.getTaskName(module, platform)
                tasks.registerTask(
                    task = RunAppleTask(runTaskName, ctx.getTaskOutputPath(runTaskName)),
                    dependsOn = listOf(buildTaskName)
                )
            }

            if (isTest) configureTestTasks()
            else configureMainTasks()
        }
}

internal enum class IosTaskType(override val prefix: String) : PlatformTaskType {
    Framework("framework"),
    BuildIosApp("buildIosApp"),
    RunIosApp("runIosApp"),
}
