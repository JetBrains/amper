/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.compilation.singleLeafFragment
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.getModuleDependencies
import org.jetbrains.amper.tasks.ios.IosTaskType
import org.jetbrains.amper.tasks.ios.ManageXCodeProjectTask
import org.jetbrains.amper.util.BuildType
import kotlin.io.path.Path

private fun isIosApp(platform: Platform, module: AmperModule) =
    platform.isDescendantOf(Platform.IOS) && module.type.isApplication()

fun ProjectTasksBuilder.setupNativeTasks() {
    tasks.registerTask(
        task = CommonizeNativeDistributionTask(
            model = model,
            userCacheRoot = context.userCacheRoot,
            incrementalCache = context.incrementalCache,
            tempRoot = context.projectTempRoot,
            jdkProvider = context.jdkProvider,
            processRunner = context.processRunner,
        )
    )

    allModules()
        .alsoPlatforms(Platform.NATIVE)
        .alsoBuildTypes()
        .alsoTests()
        .withEach {
            val fragment = module.fragments
                .filter { it.platforms.contains(platform) && it.isTest == isTest }
                .singleLeafFragment()

            val cinteropTasks = fragment.settings.native?.cinterop?.map { (moduleName, cinteropModule) ->
                val defFile = cinteropModule.defFile
                val cinteropTaskName = NativeTaskType.Cinterop.getTaskName(module, platform, isTest, buildType)
                    .let { TaskName(it.name + "-" + moduleName) }
                CinteropTask(
                    module = module,
                    platform = platform,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputRoot = context.getTaskOutputPath(cinteropTaskName),
                    incrementalCache = context.incrementalCache,
                    taskName = cinteropTaskName,
                    isTest = isTest,
                    buildType = buildType,
                    jdkProvider = context.jdkProvider,
                    defFile = module.source.moduleDir.resolve(defFile),
                    packageName = cinteropModule.packageName,
                    compilerOpts = cinteropModule.compilerOpts,
                    linkerOpts = cinteropModule.linkerOpts,
                    processRunner = context.processRunner
                ).also { tasks.registerTask(it) }
            }

            val compileKLibTaskName = NativeTaskType.CompileKLib.getTaskName(module, platform, isTest, buildType)
            tasks.registerTask(
                task = NativeCompileKlibTask(
                    module = module,
                    platform = platform,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputRoot = context.getTaskOutputPath(compileKLibTaskName),
                    incrementalCache = context.incrementalCache,
                    taskName = compileKLibTaskName,
                    tempRoot = context.projectTempRoot,
                    isTest = isTest,
                    buildType = buildType,
                    jdkProvider = context.jdkProvider,
                    processRunner = context.processRunner
                ),
                dependsOn = buildList {
                    add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                    cinteropTasks?.forEach { add(it.taskName) }
                    if (isTest) {
                        // todo (AB) : Check if this is required for test KLib compilation
                        add(NativeTaskType.CompileKLib.getTaskName(module, platform, isTest = false, buildType))
                    }
                },
            )
            if (needsLinkedExecutable(module, isTest)) {
                val (linkTaskName, compilationType) = getNativeLinkTaskDetails(platform, module, isTest, buildType)
                tasks.registerTask(
                    task = NativeLinkTask(
                        module = module,
                        platform = platform,
                        userCacheRoot = context.userCacheRoot,
                        taskOutputRoot = context.getTaskOutputPath(linkTaskName),
                        incrementalCache = context.incrementalCache,
                        taskName = linkTaskName,
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
                                    incrementalCache = context.incrementalCache
                                ).forEach { dependsOn ->
                                    add(NativeTaskType.CompileKLib.getTaskName(dependsOn, platform, false, buildType))
                                }
                            }
                        },
                        jdkProvider = context.jdkProvider,
                        processRunner = context.processRunner,
                    ),
                    dependsOn = buildList {
                        add(compileKLibTaskName)
                        cinteropTasks?.forEach { add(it.taskName) }
                        add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
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
                    taskName = runTaskName,
                    platform = platform,
                    buildType = buildType,
                    runSettings = runSettings,
                    terminal = context.terminal,
                    processRunner = context.processRunner,
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
                    taskName = CommonTaskType.Test.getTaskName(module, platform, buildType = buildType),
                    buildType = buildType,
                    platform = platform,
                    runSettings = runSettings,
                    terminal = context.terminal,
                    processRunner = context.processRunner,
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
    Cinterop("cinterop"),
}
