/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ksp

import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.frontend.schema.ModuleKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.enabled
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.compilationTaskNameFor
import org.jetbrains.amper.tasks.getModuleDependencies
import org.jetbrains.amper.util.BuildType

fun ProjectTasksBuilder.setupKspTasks() {
    allModules()
        .alsoPlatforms()
        .alsoTests()
        .withEach {
            val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
            if (fragments.none { it.settings.kotlin.ksp.enabled }) {
                return@withEach
            }
            val processorDRTaskName = CommonTaskType.KspProcessorDependencies.getTaskName(module, platform, isTest)
            tasks.registerTask(
                task = ResolveKspProcessorDependenciesTask(
                    taskName = processorDRTaskName,
                    module = module,
                    fragments = fragments,
                    platform = platform,
                    userCacheRoot = context.userCacheRoot,
                    executeOnChangedInputs = executeOnChangedInputs,
                )
            )

            // settings.kotlin.ksp.processors can't provide real AmperModule instances for now, because they are
            // parsed directly from the config files (there is no conversion like for Module -> AmperModule).
            // This is why we use paths + the module source right now to find the actual AmperModule objects.
            // TODO rework AmperModule settings so we can use different types in parsing and processing
            val processorModuleDepsPaths = fragments.flatMap { it.settings.kotlin.ksp.processors }
                .filterIsInstance<ModuleKspProcessorDeclaration>()
                .map { it.path }
            val processorModuleDeps = model.modules.filter { it.source.moduleDir in processorModuleDepsPaths }

            val processorClasspathTaskName = CommonTaskType.KspProcessorClasspath.getTaskName(module, platform, isTest)
            tasks.registerTask(
                task = KspProcessorClasspathTask(
                    module = module,
                    isTest = isTest,
                    taskName = processorClasspathTaskName,
                ),
                dependsOn = buildList {
                    add(processorDRTaskName)
                    addAll(processorModuleDeps.map { processorModuleDep ->
                        CommonTaskType.RuntimeClasspath.getTaskName(
                            module = processorModuleDep,
                            platform = Platform.JVM,
                            isTest = false,
                        )
                    })
                }
            )

            val kspTaskName = CommonTaskType.Ksp.getTaskName(module, platform, isTest)
            tasks.registerTask(
                task = KspTask(
                    module = module,
                    isTest = isTest,
                    fragments = fragments,
                    platform = platform,
                    buildOutputRoot = context.buildOutputRoot,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputRoot = context.getTaskOutputPath(kspTaskName),
                    taskName = kspTaskName,
                    executeOnChangedInputs = executeOnChangedInputs,
                    tempRoot = context.projectTempRoot,
                ),
                dependsOn = buildList {
                    add(CommonTaskType.KspProcessorClasspath.getTaskName(module, platform, isTest))

                    // we also need all compile dependencies when passing code to KSP (for resolution)
                    // TODO create a general compileClasspath task for reuse?
                    add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                    if (platform.isDescendantOf(Platform.ANDROID)) {
                        add(CommonTaskType.TransformDependencies.getTaskName(module, platform))
                    }
                    if (isTest) {
                        // test compilation depends on main classes
                        add(compilationTaskNameFor(module, platform, isTest = false, buildType = BuildType.Debug))
                    }
                    module.getModuleDependencies(isTest, platform, ResolutionScope.COMPILE, context.userCacheRoot).forEach {
                        add(compilationTaskNameFor(it, platform, isTest = false,  buildType = BuildType.Debug))

                        // TODO add transitive 'exported' dependencies from module deps
                    }
                },
            )
        }

    // TODO register KSP tasks on common fragments
    //   How? Should we do each fragment with more than 2 platforms + its fragment dependencies?
}
