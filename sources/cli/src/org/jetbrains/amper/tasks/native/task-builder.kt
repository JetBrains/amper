/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.DependencyReason
import org.jetbrains.amper.tasks.FragmentSelector
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.ios.IosTaskType
import org.jetbrains.amper.util.BuildType

private fun isIosApp(platform: Platform, module: PotatoModule) =
    platform.isDescendantOf(Platform.IOS) && module.type.isApplication()

fun ProjectTaskRegistrar.setupNativeTasks() {
    forWholeModel { model, eonci ->
        registerTask(
            task = CommonizeNativeDistributionTask(
                model = model,
                userCacheRoot = context.userCacheRoot,
                executeOnChangedInputs = eonci,
                tempRoot = context.projectTempRoot,
            )
        )
    }

    FragmentSelector.leafFragments().platform(Platform.NATIVE).select { (executeOnChangedInputs, _, module, isTest, platform) ->
        platform ?: return@select
        val compileKLibTaskName = NativeTaskType.CompileKLib.getTaskName(module, platform, isTest)
        registerTask(
            task = NativeCompileKlibTask(
                module = module,
                platform = platform,
                userCacheRoot = context.userCacheRoot,
                taskOutputRoot = context.getTaskOutputPath(compileKLibTaskName),
                executeOnChangedInputs = executeOnChangedInputs,
                taskName = compileKLibTaskName,
                tempRoot = context.projectTempRoot,
                isTest = isTest,
            ),
            dependsOn = buildList {
                add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                if (isTest) {
                    // todo (AB) : Check if this is required for test KLib compilation
                    add(NativeTaskType.CompileKLib.getTaskName(module, platform, isTest = false))
                }
            },
        )
        if (needsLinkedExecutable(module, isTest)) {
            val (linkAppTaskName, compilationType) = getNativeLinkTaskDetails(platform, module, isTest)
            registerTask(
                task = NativeLinkTask(
                    module = module,
                    platform = platform,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputRoot = context.getTaskOutputPath(linkAppTaskName),
                    executeOnChangedInputs = executeOnChangedInputs,
                    taskName = linkAppTaskName,
                    tempRoot = context.projectTempRoot,
                    isTest = isTest,
                    compilationType = compilationType,
                    compileKLibTaskName = compileKLibTaskName,
                ),
                dependsOn = buildList {
                    add(compileKLibTaskName)
                    add(CommonTaskType.Dependencies.getTaskName(module, platform, false))
                    if (isTest) {
                        add(NativeTaskType.CompileKLib.getTaskName(module, platform, isTest = false))
                    }
                }
            )
        }
    }

    FragmentSelector.leafFragments().platform(Platform.NATIVE).selectModuleDependencies(DependencyReason.Runtime) {
        it.platform ?: return@selectModuleDependencies
        registerDependency(
            NativeTaskType.CompileKLib.getTaskName(it.module, it.platform, it.isTest),
            NativeTaskType.CompileKLib.getTaskName(it.dependsOn, it.platform, false)
        )

        if (needsLinkedExecutable(it.module, it.isTest)) {
            registerDependency(
                getNativeLinkTaskName(it.platform, it.module, it.isTest),
                NativeTaskType.CompileKLib.getTaskName(it.dependsOn, it.platform, false)
            )
        }
    }

    FragmentSelector.leafFragments().platform(Platform.NATIVE).test(false).select { (_, _, module, _, platform) ->
        platform ?: return@select
        // Skip running of ios/app modules, since it is handled in [ios.task-builder.kt].
        if (isIosApp(platform, module)) return@select

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

    FragmentSelector.leafFragments().platform(Platform.NATIVE).test(true).select { (_, _, module, _, platform) ->
        platform ?: return@select

        // Skip testing of ios/app modules, since it is handled in [ios.task-builder.kt].
        if (isIosApp(platform, module)) return@select

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

private fun needsLinkedExecutable(module: PotatoModule, isTest: Boolean) =
    module.type.isApplication() || isTest

private fun getNativeLinkTaskName(platform: Platform, module: PotatoModule, isTest: Boolean) =
    getNativeLinkTaskDetails(platform, module, isTest).first

private fun getNativeLinkTaskDetails(
    platform: Platform,
    module: PotatoModule,
    isTest: Boolean
) = when {
    isIosApp(platform, module) && !isTest ->
        IosTaskType.Framework.getTaskName(module, platform, false, BuildType.Debug) to KotlinCompilationType.IOS_FRAMEWORK
    else ->
        NativeTaskType.Link.getTaskName(module, platform, isTest) to KotlinCompilationType.BINARY
}

enum class NativeTaskType(override val prefix: String) : PlatformTaskType {
    CompileKLib("compile"),
    Link("link"),
}
