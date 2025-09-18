/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.js

import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath

fun ProjectTasksBuilder.setupJsTasks() {

    allModules()
        .alsoPlatforms(Platform.JS)
        .alsoTests()
        .withEach {
            val compileKLibTaskName = JsTaskType.CompileKLib.getTaskName(module, platform, isTest)
            tasks.registerTask(
                task = JsCompileKlibTask(
                    module = module,
                    platform = platform,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputRoot = context.getTaskOutputPath(compileKLibTaskName),
                    incrementalCache = executeOnChangedInputs,
                    taskName = compileKLibTaskName,
                    tempRoot = context.projectTempRoot,
                    isTest = isTest,
                ),
                dependsOn = buildList {
                    add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                    if (isTest) {
                        // todo (AB) : Check if this is required for test KLib compilation
                        add(JsTaskType.CompileKLib.getTaskName(module, platform, isTest = false))
                    }
                },
            )

            if (needsLinkedExecutable(module, isTest)) {
                val linkAppTaskName = JsTaskType.Link.getTaskName(module, platform, isTest)
                tasks.registerTask(
                    task = JsLinkTask(
                        module = module,
                        platform = platform,
                        userCacheRoot = context.userCacheRoot,
                        taskOutputRoot = context.getTaskOutputPath(linkAppTaskName),
                        incrementalCache = executeOnChangedInputs,
                        taskName = linkAppTaskName,
                        tempRoot = context.projectTempRoot,
                        isTest = isTest,
                        compileKLibTaskName = compileKLibTaskName,
                    ),
                    dependsOn = buildList {
                        add(compileKLibTaskName)
                        add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                        if (isTest) {
                            add(JsTaskType.CompileKLib.getTaskName(module, platform, isTest = false))
                        }
                    }
                )
            }
        }

    allModules()
        .alsoPlatforms(Platform.JS)
        .alsoTests()
        .selectModuleDependencies(ResolutionScope.RUNTIME).withEach {
            tasks.registerDependency(
                JsTaskType.CompileKLib.getTaskName(module, platform, isTest),
                JsTaskType.CompileKLib.getTaskName(dependsOn, platform, false)
            )

            if (needsLinkedExecutable(module, isTest)) {
                tasks.registerDependency(
                    JsTaskType.Link.getTaskName(module, platform, isTest),
                    JsTaskType.CompileKLib.getTaskName(dependsOn, platform, false)
                )
            }
        }
}

private fun needsLinkedExecutable(module: AmperModule, isTest: Boolean) =
    module.type.isApplication() || isTest

enum class JsTaskType(override val prefix: String) : PlatformTaskType {
    CompileKLib("compile"),
    Link("link"),
}