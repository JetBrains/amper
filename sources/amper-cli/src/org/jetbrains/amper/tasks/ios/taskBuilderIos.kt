/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.compose.isComposeEnabledFor
import org.jetbrains.amper.tasks.native.NativeTaskType

/**
 * Set up apple-related tasks.
 */
fun ProjectTasksBuilder.setupIosTasks() {
    allModules()
        .alsoPlatforms(Platform.IOS)
        .alsoBuildTypes()
        .filter {
            // Tests only make sense for simulator targets
            it.platform.isIosSimulator
        }
        .withEach {
            // TODO: compose resources for iOS tests?
            tasks.registerTask(
                task = IosKotlinTestTask(
                    taskName = CommonTaskType.Test.getTaskName(module, platform, isTest = false, buildType),
                    module = module,
                    projectRoot = context.projectRoot,
                    terminal = context.terminal,
                    runSettings = context.runSettings,
                    platform = platform,
                    buildType = buildType,
                ),
                dependsOn = NativeTaskType.Link.getTaskName(module, platform, isTest = true, buildType)
            )
        }

    allModules()
        .filterModuleType { it == ProductType.IOS_APP }
        .withEach {
            tasks.registerTask(
                task = ManageXCodeProjectTask(
                    module = module,
                ),
            )
        }

    allModules()
        .alsoPlatforms(Platform.IOS)
        .filterModuleType { it == ProductType.IOS_APP }
        .filter { isComposeEnabledFor(it.module) }
        .withEach {
            val taskName = IosTaskType.PrepareComposeResources.getTaskName(module, platform)
            tasks.registerTask(
                task = IosComposeResourcesTask(
                    taskName = taskName,
                    leafFragment = module.leafFragments.single {
                        it.platform == platform && !it.isTest
                    },
                    executeOnChangedInputs = executeOnChangedInputs,
                    taskOutputRoot = context.getTaskOutputPath(taskName),
                    userCacheRoot = context.userCacheRoot,
                ),
            )
        }

    allModules()
        .alsoPlatforms(Platform.IOS)
        .alsoBuildTypes()
        .filterModuleType { it == ProductType.IOS_APP }
        .withEach {
            val preBuildTaskName = IosTaskType.PreBuildIosApp.getTaskName(module, platform, false, buildType)
            tasks.registerTask(
                task = IosPreBuildTask(
                    taskName = preBuildTaskName,
                ),
                dependsOn = buildList {
                    if (isComposeEnabledFor(module)) {
                        add(IosTaskType.PrepareComposeResources.getTaskName(module, platform))
                    }
                    add(IosTaskType.Framework.getTaskName(module, platform, isTest = false, buildType))
                },
            )

            val buildTaskName = IosTaskType.BuildIosApp.getTaskName(module, platform, isTest = false, buildType)
            tasks.registerTask(
                task = IosBuildTask(
                    platform = platform,
                    module = module,
                    buildType = buildType,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputPath = context.getTaskOutputPath(buildTaskName),
                    taskName = buildTaskName,
                ),
                dependsOn = listOf(
                    preBuildTaskName,
                    // This goes here instead of pre-build because if the build is run from xcode, then managing the
                    // project won't help much anyway.
                    ManageXCodeProjectTask.taskName(module),
                ),
            )

            val runTaskName = IosTaskType.RunIosApp.getTaskName(module, platform, isTest = false, buildType)
            tasks.registerTask(
                task = IosRunTask(
                    taskName = runTaskName,
                    platform = platform,
                    buildType = buildType,
                    module = module,
                    runSettings = context.runSettings,
                    taskOutputPath = context.getTaskOutputPath(runTaskName),
                ),
                dependsOn = listOf(buildTaskName)
            )
        }
}

internal enum class IosTaskType(override val prefix: String) : PlatformTaskType {
    Framework("framework"),
    BuildIosApp("buildIosApp"),
    RunIosApp("runIosApp"),
    PrepareComposeResources("prepareComposeResourcesForIos"),
    PreBuildIosApp("preBuildIosApp")
}
