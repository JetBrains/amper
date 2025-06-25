/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import org.jetbrains.amper.compilation.KotlinCompilationType
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.getModuleDependencies
import org.jetbrains.amper.tasks.ios.ManageXCodeProjectTask
import org.jetbrains.amper.tasks.native.NativeLinkTask
import org.jetbrains.amper.tasks.native.NativeTaskType
import org.jetbrains.amper.tasks.native.getNativeLinkTaskDetails

fun ProjectTasksBuilder.setupWasmJsTasks() {

    allModules()
        .alsoPlatforms(Platform.WASM)
        .alsoTests()
        .withEach {
            val compileKLibTaskName = WasmJsTaskType.CompileKLib.getTaskName(module, platform, isTest)
            tasks.registerTask(
                task = WasmJsCompileKlibTask(
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
                        add(WasmJsTaskType.CompileKLib.getTaskName(module, platform, isTest = false))
                    }
                },
            )

            if (needsLinkedExecutable(module, isTest)) {
                val (linkAppTaskName, compilationType) = getNativeLinkTaskDetails(platform, module, isTest)
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
                                    add(NativeTaskType.CompileKLib.getTaskName(dependsOn, platform, false))
                                }
                            }
                        },
                    ),
                    dependsOn = buildList {
                        add(compileKLibTaskName)
                        add(CommonTaskType.Dependencies.getTaskName(module, platform, false))
                        if (isTest) {
                            add(NativeTaskType.CompileKLib.getTaskName(module, platform, isTest = false))
                        }
                        if (compilationType == KotlinCompilationType.IOS_FRAMEWORK) {
                            // Needed for bundleId inference
                            add(ManageXCodeProjectTask.taskName(module))
                        }
                    }
                )
            }
        }
}

private fun needsLinkedExecutable(module: AmperModule, isTest: Boolean) =
    module.type.isApplication() || isTest

enum class WasmJsTaskType(override val prefix: String) : PlatformTaskType {
    CompileKLib("compile"),
    Link("link"),
}