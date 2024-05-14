/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath

private fun getProductionSourcesForTestsCompileTaskName(module: PotatoModule, platform: Platform): TaskName {
    val compileTaskName = CommonTaskType.Compile.getTaskName(module, platform, false)
    return if (module.type.isApplication()) {
        TaskName(compileTaskName.name + "Library")
    } else {
        compileTaskName
    }
}

private fun isIosApp(platform: Platform, module: PotatoModule) =
    platform.isDescendantOf(Platform.IOS) && module.type.isApplication()

fun ProjectTaskRegistrar.setupNativeTasks() {
    onEachTaskType(Platform.NATIVE) { module, executeOnChangedInputs, platform, isTest ->
        // Skip native compilation of ios/app modules, since it is handled in [ios.task-builder.kt].
        if (isIosApp(platform, module)) return@onEachTaskType

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
                terminal = context.terminal,
            ),
            CommonTaskType.Dependencies.getTaskName(module, platform, isTest)
        )

        if (module.type.isApplication() && !isTest) {
            // Application compilation generates executable file which is not usable for tests compilation
            // Let's prepare separate klib, which will be linked to test code

            registerTask(
                NativeCompileTask(
                    module = module,
                    platform = platform,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputRoot = context.getTaskOutputPath(compileTaskName),
                    executeOnChangedInputs = executeOnChangedInputs,
                    taskName = getProductionSourcesForTestsCompileTaskName(module, platform),
                    tempRoot = context.projectTempRoot,
                    isTest = false,
                    compilationType = KotlinCompilationType.LIBRARY,
                    terminal = context.terminal,
                ),
                CommonTaskType.Dependencies.getTaskName(module, platform, false)
            )
        }
    }

    onCompileModuleDependency(Platform.NATIVE) { module, dependsOn, _, platform, isTest ->
        // Skip native compilation of ios/app modules, since it is handled in [ios.task-builder.kt].
        if (isIosApp(platform, module)) return@onCompileModuleDependency

        if (isTest) {
            registerDependency(
                CommonTaskType.Compile.getTaskName(module, platform, true),
                CommonTaskType.Compile.getTaskName(dependsOn, platform, false)
            )
        } else {
            // Two prod compile configurations, one is building an app, another is building klib for linking with tests
            for (compileTaskName in setOf(
                getProductionSourcesForTestsCompileTaskName(module, platform),
                CommonTaskType.Compile.getTaskName(module, platform, false))
            ) {
                registerDependency(
                    compileTaskName,
                    CommonTaskType.Compile.getTaskName(dependsOn, platform, false)
                )
            }
        }
    }

    onMain(Platform.NATIVE) { module, _, platform, _ ->
        // Skip running of ios/app modules, since it is handled in [ios.task-builder.kt].
        if (isIosApp(platform, module)) return@onMain

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
            CommonTaskType.Compile.getTaskName(module, platform, false)
        )
    }

    onTest(Platform.NATIVE) { module, _, platform, _ ->
        // Skip testing of ios/app modules, since it is handled in [ios.task-builder.kt].
        if (isIosApp(platform, module)) return@onTest

        val compileTestsTaskName = CommonTaskType.Compile.getTaskName(module, platform, true)
        val testTaskName = CommonTaskType.Test.getTaskName(module, platform)

        val sourcesForTestsCompileTaskName = getProductionSourcesForTestsCompileTaskName(module, platform)

        registerTask(
            NativeTestTask(
                module = module,
                projectRoot = context.projectRoot,
                taskName = testTaskName,
                platform = platform,
                terminal = context.terminal,
            ),
            compileTestsTaskName
        )
        registerDependency(
            taskName = compileTestsTaskName,
            dependsOn = sourcesForTestsCompileTaskName,
        )
    }
}
