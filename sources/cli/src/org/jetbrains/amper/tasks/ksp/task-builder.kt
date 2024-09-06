/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ksp

import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.schema.enabled
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.native.NativeTaskType
import org.jetbrains.amper.util.BuildType

fun ProjectTasksBuilder.setupKspTasks() {
    allModules()
        .alsoPlatforms()
        .withEach {
            val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
            if (fragments.any { it.settings.ksp.enabled }) {
                val kspTaskName = CommonTaskType.Ksp.getTaskName(module, platform, isTest)
                tasks.registerTask(
                    task = KspTask(
                        module = module,
                        isTest = isTest,
                        fragments = fragments,
                        platform = platform,
                        userCacheRoot = context.userCacheRoot,
                        taskOutputRoot = context.getTaskOutputPath(kspTaskName),
                        taskName = kspTaskName,
                        executeOnChangedInputs = executeOnChangedInputs,
                        tempRoot = context.projectTempRoot,
                    ),
                    dependsOn = buildList {
                        add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                        if (platform.isDescendantOf(Platform.ANDROID)) {
                            add(CommonTaskType.TransformDependencies.getTaskName(module, platform))
                        }
                    }
                )
                if (platform.isDescendantOf(Platform.ANDROID)) {
                    BuildType.entries.forEach { buildType ->
                        tasks.registerDependency(
                            CommonTaskType.Compile.getTaskName(
                                module,
                                platform,
                                isTest,
                                buildType
                            ), kspTaskName
                        )
                    }
                } else if (platform.isDescendantOf(Platform.NATIVE)) {
                    tasks.registerDependency(
                        NativeTaskType.CompileKLib.getTaskName(module, platform, isTest),
                        kspTaskName
                    )
                } else {
                    tasks.registerDependency(
                        CommonTaskType.Compile.getTaskName(module, platform, isTest),
                        kspTaskName
                    )
                }
            }
        }

    allModules()
        .alsoPlatforms()
        .selectModuleDependencies(ResolutionScope.COMPILE) {
            val fragments =
                module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
            if (fragments.any { it.settings.ksp.enabled }) {
                tasks.registerDependency(
                    taskName = CommonTaskType.Ksp.getTaskName(module, platform, isTest),
                    dependsOn = CommonTaskType.Compile.getTaskName(dependsOn, platform, isTest = false)
                )
            }
        }

    // TODO register KSP tasks on common fragments
    //   How? Should we do each fragment with more than 2 platforms + its fragment dependencies?
}
