/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath

fun ProjectTasksBuilder.setupWasmJsTasks() {

    allModules()
        .alsoPlatforms(Platform.WASM_JS)
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
                val linkAppTaskName = WasmJsTaskType.Link.getTaskName(module, platform, isTest)
                tasks.registerTask(
                    task = WasmJsLinkTask(
                        module = module,
                        platform = platform,
                        userCacheRoot = context.userCacheRoot,
                        taskOutputRoot = context.getTaskOutputPath(linkAppTaskName),
                        executeOnChangedInputs = executeOnChangedInputs,
                        taskName = linkAppTaskName,
                        tempRoot = context.projectTempRoot,
                        isTest = isTest,
                        compileKLibTaskName = compileKLibTaskName,
                    ),
                    dependsOn = buildList {
                        add(compileKLibTaskName)
                        add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                        if (isTest) {
                            add(WasmJsTaskType.CompileKLib.getTaskName(module, platform, isTest = false))
                        }
                    }
                )
            }
        }

    allModules()
        .alsoPlatforms(Platform.WASM_JS)
        .alsoTests()
        .selectModuleDependencies(ResolutionScope.RUNTIME).withEach {
            tasks.registerDependency(
                WasmJsTaskType.CompileKLib.getTaskName(module, platform, isTest),
                WasmJsTaskType.CompileKLib.getTaskName(dependsOn, platform, false)
            )

            if (needsLinkedExecutable(module, isTest)) {
                tasks.registerDependency(
                    WasmJsTaskType.Link.getTaskName(module, platform, isTest),
                    WasmJsTaskType.CompileKLib.getTaskName(dependsOn, platform, false)
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