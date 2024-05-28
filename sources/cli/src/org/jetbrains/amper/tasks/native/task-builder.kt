/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath

private fun isIosApp(platform: Platform, module: PotatoModule) =
    platform.isDescendantOf(Platform.IOS) && module.type.isApplication()

fun ProjectTaskRegistrar.setupNativeTasks() {
    onEachTaskType(Platform.NATIVE) { module, executeOnChangedInputs, platform, isTest ->
        val compileKLibTaskName = NativeTaskType.CompileKLib.getTaskName(module, platform, isTest)
        registerTask(
            task = NativeCompileTask(
                module = module,
                platform = platform,
                userCacheRoot = context.userCacheRoot,
                taskOutputRoot = context.getTaskOutputPath(compileKLibTaskName),
                executeOnChangedInputs = executeOnChangedInputs,
                taskName = compileKLibTaskName,
                tempRoot = context.projectTempRoot,
                isTest = isTest,
                terminal = context.terminal,
                compilationType = KotlinCompilationType.LIBRARY,
            ),
            dependsOn = buildList {
                add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                if (isTest) {
                    add(NativeTaskType.CompileKLib.getTaskName(module, platform, isTest = false))
                }
            },
        )
        val needsLinkedExecutable = module.type.isApplication() || isTest
        // iOS framework task is defined by the iOS task builder
        if (needsLinkedExecutable && !isIosApp(platform, module)) {
            val linkAppTaskName = NativeTaskType.Link.getTaskName(module, platform, isTest)
            registerTask(
                NativeCompileTask(
                    module = module,
                    platform = platform,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputRoot = context.getTaskOutputPath(linkAppTaskName),
                    executeOnChangedInputs = executeOnChangedInputs,
                    taskName = linkAppTaskName,
                    tempRoot = context.projectTempRoot,
                    isTest = isTest,
                    terminal = context.terminal,
                    compilationType = KotlinCompilationType.BINARY,
                ),
                listOf(
                    compileKLibTaskName,
                    CommonTaskType.Dependencies.getTaskName(module, platform, isTest),
                ),
            )
        }
    }

    onCompileModuleDependency(Platform.NATIVE) { module, dependsOn, _, platform, isTest ->
        registerDependency(
            NativeTaskType.CompileKLib.getTaskName(module, platform, isTest),
            NativeTaskType.CompileKLib.getTaskName(dependsOn, platform, false)
        )
    }

    onMain(Platform.NATIVE) { module, _, platform, _ ->
        // Skip running of ios/app modules, since it is handled in [ios.task-builder.kt].
        if (isIosApp(platform, module)) return@onMain

        if (module.type.isApplication()) {
            val runTaskName = CommonTaskType.Run.getTaskName(module, platform)
            registerTask(
                NativeRunTask(
                    module = module,
                    projectRoot = context.projectRoot,
                    taskName = runTaskName,
                    platform = platform,
                    commonRunSettings = context.commonRunSettings,
                    terminal = context.terminal,
                ),
                NativeTaskType.Link.getTaskName(module, platform, isTest = false)
            )
        }
    }

    onTest(Platform.NATIVE) { module, _, platform, _ ->
        // Skip testing of ios/app modules, since it is handled in [ios.task-builder.kt].
        if (isIosApp(platform, module)) return@onTest

        val testTaskName = CommonTaskType.Test.getTaskName(module, platform)

        registerTask(
            NativeTestTask(
                module = module,
                projectRoot = context.projectRoot,
                taskName = testTaskName,
                platform = platform,
                terminal = context.terminal,
            ),
            NativeTaskType.Link.getTaskName(module, platform, isTest = true)
        )
    }
}

enum class NativeTaskType(override val prefix: String) : PlatformTaskType {
    CompileKLib("compile"),
    Link("link"),
}
