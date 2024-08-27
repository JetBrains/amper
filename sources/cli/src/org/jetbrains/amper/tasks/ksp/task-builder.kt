/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ksp

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.schema.*
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.native.NativeTaskType
import org.jetbrains.amper.util.BuildType

fun ProjectTaskRegistrar.setupKspTasks() {
    onEachTaskType { module, executeOnChangedInputs, platform, isTest ->
        val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
        if (fragments.any { it.settings.ksp.enabled }) {
            val kspTaskName = CommonTaskType.Ksp.getTaskName(module, platform, isTest)
            registerTask(
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
                BuildType.entries.forEach {
                    registerDependency(CommonTaskType.Compile.getTaskName(module, platform, isTest, it), kspTaskName)
                }
            } else if (platform.isDescendantOf(Platform.NATIVE)) {
                registerDependency(NativeTaskType.CompileKLib.getTaskName(module, platform, isTest), kspTaskName)
            } else {
                registerDependency(CommonTaskType.Compile.getTaskName(module, platform, isTest), kspTaskName)
            }
        }
    }

    // FIXME this registers task dependencies twice for platforms that don't have build types.
    //   This code relies on the fact that getTaskName() will ignore the buildType on non-Android platforms.
    onCompileModuleDependency(Platform.COMMON) { module, dependsOn, _, platform, isTest, buildType ->
        val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
        if (fragments.any { it.settings.ksp.enabled }) {
            registerDependency(
                taskName = CommonTaskType.Ksp.getTaskName(module, platform, isTest),
                dependsOn = CommonTaskType.Compile.getTaskName(dependsOn, platform, isTest = false, buildType)
            )
        }
    }

    // TODO register KSP tasks on common fragments
    //   How? Should we do each fragment with more than 2 platforms + its fragment dependencies?
}
