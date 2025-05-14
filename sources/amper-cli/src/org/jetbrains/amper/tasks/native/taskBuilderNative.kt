/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.getModuleDependencies
import org.jetbrains.amper.tasks.ios.IosTaskType
import org.jetbrains.amper.tasks.ios.ManageXCodeProjectTask
import org.jetbrains.amper.util.BuildType

private fun isIosApp(platform: Platform, module: AmperModule) =
    platform.isDescendantOf(Platform.IOS) && module.type.isApplication()

fun ProjectTasksBuilder.setupNativeTasks() {
    tasks.registerTask(
        task = CommonizeNativeDistributionTask(
            model = model,
            userCacheRoot = context.userCacheRoot,
            executeOnChangedInputs = executeOnChangedInputs,
            tempRoot = context.projectTempRoot,
        )
    )

    allModules()
        .alsoPlatforms(Platform.NATIVE)
        .alsoBuildTypes()
        .alsoTests()
        .withEach {
            val compileKLibTaskName = NativeTaskType.CompileKLib.getTaskName(module, platform, isTest, buildType)
            tasks.registerTask(
                task = NativeCompileKlibTask(
                    module = module,
                    platform = platform,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputRoot = context.getTaskOutputPath(compileKLibTaskName),
                    executeOnChangedInputs = executeOnChangedInputs,
                    taskName = compileKLibTaskName,
                    tempRoot = context.projectTempRoot,
                    isTest = isTest,
                    buildType = buildType,
                ),
                dependsOn = buildList {
                    add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                    if (isTest) {
                        // todo (AB) : Check if this is required for test KLib compilation
                        add(NativeTaskType.CompileKLib.getTaskName(module, platform, isTest = false, buildType))
                    }
                },
            )
            if (needsLinkedExecutable(module, isTest)) {
                val (linkAppTaskName, compilationType) = getNativeLinkTaskDetails(platform, module, isTest, buildType)
                tasks.registerTask(
                    task = NativeLinkTask(
                        module = module,
                        platform = platform,
                        userCacheRoot = context.userCacheRoot,
                        taskOutputRoot = context.getTaskOutputPath(linkAppTaskName),
                        executeOnChangedInputs = executeOnChangedInputs,
                        taskName = linkAppTaskName,
                        tempRoot = context.projectTempRoot,
                        isTest = isTest,
                        buildType = buildType,
                        compilationType = compilationType,
                        compileKLibTaskName = compileKLibTaskName,
                        exportedKLibTaskNames = buildSet {
                            // Build the exported libraries set for iOS
                            if (compilationType == KotlinCompilationType.IOS_FRAMEWORK) {
                                module.getModuleDependencies(
                                    isTest = false,
                                    platform = platform,
                                    dependencyReason = ResolutionScope.COMPILE,
                                    userCacheRoot = context.userCacheRoot,
                                ).forEach { dependsOn ->
                                    add(NativeTaskType.CompileKLib.getTaskName(dependsOn, platform, false, buildType))
                                }
                            }
                        },
                    ),
                    dependsOn = buildList {
                        add(compileKLibTaskName)
                        add(CommonTaskType.Dependencies.getTaskName(module, platform, false))
                        if (isTest) {
                            add(NativeTaskType.CompileKLib.getTaskName(module, platform, isTest = false, buildType))
                        }
                        if (compilationType == KotlinCompilationType.IOS_FRAMEWORK) {
                            // Needed for bundleId inference
                            add(ManageXCodeProjectTask.taskName(module))
                        }
                    }
                )
            }
        }

    allModules()
        .alsoPlatforms(Platform.NATIVE)
        .alsoTests()
        .alsoBuildTypes()
        .selectModuleDependencies(ResolutionScope.RUNTIME).withEach {
            tasks.registerDependency(
                NativeTaskType.CompileKLib.getTaskName(module, platform, isTest, buildType),
                NativeTaskType.CompileKLib.getTaskName(dependsOn, platform, false, buildType)
            )

            if (needsLinkedExecutable(module, isTest)) {
                tasks.registerDependency(
                    getNativeLinkTaskName(platform, module, isTest, buildType),
                    NativeTaskType.CompileKLib.getTaskName(dependsOn, platform, false, buildType)
                )
            }
        }

    allModules()
        .alsoPlatforms(Platform.NATIVE)
        .filter {
            // Skip running of ios/app modules, since it is handled in taskBuilderIos.kt
            it.module.type.isApplication() && !it.platform.isDescendantOf(Platform.IOS)
        }
        .alsoBuildTypes()
        .withEach {
            val runTaskName = CommonTaskType.Run.getTaskName(module, platform, isTest = false, buildType)
            tasks.registerTask(
                NativeRunTask(
                    module = module,
                    projectRoot = context.projectRoot,
                    taskName = runTaskName,
                    platform = platform,
                    buildType = buildType,
                    commonRunSettings = context.commonRunSettings,
                    terminal = context.terminal,
                ),
                NativeTaskType.Link.getTaskName(module, platform, isTest = false, buildType)
            )
        }

    allModules()
        .alsoPlatforms(Platform.NATIVE)
        .filterNot {
            // Skip testing of ios modules, since it is handled in taskBuilderIos.kt
            it.platform.isDescendantOf(Platform.IOS)
        }
        .alsoBuildTypes()
        .withEach {
            tasks.registerTask(
                NativeTestTask(
                    module = module,
                    projectRoot = context.projectRoot,
                    taskName = CommonTaskType.Test.getTaskName(module, platform, buildType = buildType),
                    buildType = buildType,
                    platform = platform,
                    commonRunSettings = context.commonRunSettings,
                    terminal = context.terminal,
                ),
                NativeTaskType.Link.getTaskName(module, platform, isTest = true, buildType)
            )
        }
}

private fun needsLinkedExecutable(module: AmperModule, isTest: Boolean) =
    module.type.isApplication() || isTest

private fun getNativeLinkTaskName(platform: Platform, module: AmperModule, isTest: Boolean, buildType: BuildType) =
    getNativeLinkTaskDetails(platform, module, isTest, buildType).first

private fun getNativeLinkTaskDetails(
    platform: Platform,
    module: AmperModule,
    isTest: Boolean,
    buildType: BuildType,
) = when {
    isIosApp(platform, module) && !isTest ->
        IosTaskType.Framework.getTaskName(
            module,
            platform,
            false,
            buildType,
        ) to KotlinCompilationType.IOS_FRAMEWORK

    else ->
        NativeTaskType.Link.getTaskName(module, platform, isTest, buildType) to KotlinCompilationType.BINARY
}

enum class NativeTaskType(override val prefix: String) : PlatformTaskType {
    CompileKLib("compile"),
    Link("link"),
}
