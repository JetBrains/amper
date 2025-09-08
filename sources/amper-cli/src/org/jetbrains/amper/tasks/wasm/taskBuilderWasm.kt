/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.wasm

import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.web.WebCompileKlibTask
import org.jetbrains.amper.tasks.web.WebLinkTask

internal fun ProjectTasksBuilder.setupWasmTasks(
    platform: Platform,
    createCompileTask: (
        module: AmperModule,
        platform: Platform,
        userCacheRoot: AmperUserCacheRoot,
        taskOutputRoot: TaskOutputRoot,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        taskName: TaskName,
        tempRoot: AmperProjectTempRoot,
        isTest: Boolean,
    ) -> WebCompileKlibTask,
    createLinkTask: (
        module: AmperModule,
        platform: Platform,
        userCacheRoot: AmperUserCacheRoot,
        taskOutputRoot: TaskOutputRoot,
        executeOnChangedInputs: ExecuteOnChangedInputs,
        taskName: TaskName,
        tempRoot: AmperProjectTempRoot,
        isTest: Boolean,
        compileKLibTaskName: TaskName,
    ) -> WebLinkTask,
) {

    allModules()
        .alsoPlatforms(platform)
        .alsoTests()
        .withEach {
            val compileKLibTaskName = WasmTaskType.CompileKLib.getTaskName(module, platform, isTest)
            tasks.registerTask(
                task = createCompileTask(
                    module,
                    platform,
                    context.userCacheRoot,
                    context.getTaskOutputPath(compileKLibTaskName),
                    executeOnChangedInputs,
                    compileKLibTaskName,
                    context.projectTempRoot,
                    isTest,
                ),
                dependsOn = buildList {
                    add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                    if (isTest) {
                        // todo (AB) : Check if this is required for test KLib compilation
                        add(WasmTaskType.CompileKLib.getTaskName(module, platform, isTest = false))
                    }
                },
            )

            if (needsLinkedExecutable(module, isTest)) {
                val linkAppTaskName = WasmTaskType.Link.getTaskName(module, platform, isTest)
                tasks.registerTask(
                    task = createLinkTask(
                        module,
                        platform,
                        context.userCacheRoot,
                        context.getTaskOutputPath(linkAppTaskName),
                        executeOnChangedInputs,
                        linkAppTaskName,
                        context.projectTempRoot,
                        isTest,
                        compileKLibTaskName,
                    ),
                    dependsOn = buildList {
                        add(compileKLibTaskName)
                        add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                        if (isTest) {
                            add(WasmTaskType.CompileKLib.getTaskName(module, platform, isTest = false))
                        }
                    }
                )
            }
        }

    allModules()
        .alsoPlatforms(platform)
        .alsoTests()
        .selectModuleDependencies(ResolutionScope.RUNTIME).withEach {
            tasks.registerDependency(
                WasmTaskType.CompileKLib.getTaskName(module, platform, isTest),
                WasmTaskType.CompileKLib.getTaskName(dependsOn, platform, false)
            )

            if (needsLinkedExecutable(module, isTest)) {
                tasks.registerDependency(
                    WasmTaskType.Link.getTaskName(module, platform, isTest),
                    WasmTaskType.CompileKLib.getTaskName(dependsOn, platform, false)
                )
            }
        }
}

enum class WasmTaskType(override val prefix: String) : PlatformTaskType {
    CompileKLib("compile"),
    Link("link"),
}

private fun needsLinkedExecutable(module: AmperModule, isTest: Boolean) =
    module.type.isApplication() || isTest