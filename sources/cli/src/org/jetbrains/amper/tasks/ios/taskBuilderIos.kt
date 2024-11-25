/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ios

import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.compose.ComposeFragmentTaskType
import org.jetbrains.amper.tasks.compose.isComposeEnabledFor
import org.jetbrains.amper.tasks.fragmentsTargeting
import org.jetbrains.amper.tasks.getModuleDependencies
import org.jetbrains.amper.tasks.native.NativeTaskType
import org.jetbrains.amper.util.BuildType

/**
 * Set up apple-related tasks.
 */
fun ProjectTasksBuilder.setupIosTasks() {
    allModules()
        .alsoPlatforms(Platform.IOS)
        .withEach {
            // TODO: compose resources for iOS tests?
            tasks.registerTask(
                task = IosKotlinTestTask(
                    taskName = CommonTaskType.Test.getTaskName(module, platform),
                    module = module,
                    projectRoot = context.projectRoot,
                    terminal = context.terminal,
                    platform = platform,
                ),
                dependsOn = NativeTaskType.Link.getTaskName(module, platform, isTest = true)
            )
        }

    allModules()
        .filterModuleType { it == ProductType.IOS_APP }
        .withEach {
            tasks.registerTask(
                task = ManageXCodeProjectTask(
                    module = module,
                ),
            )
        }

    allModules()
        .alsoPlatforms(Platform.IOS)
        .filterModuleType { it == ProductType.IOS_APP }
        .withEach {
            val composeResourcesTaskName = IosTaskType.PrepareComposeResources.getTaskName(module, platform)
                    .takeIf { isComposeEnabledFor(module) }
                    ?.also { taskName ->
                        tasks.registerTask(
                            task = IosComposeResourcesTask(
                                taskName = taskName,
                                leafFragment = module.leafFragments.single {
                                    it.platform == platform && !it.isTest
                                },
                                buildOutputRoot = context.buildOutputRoot,
                                executeOnChangedInputs = executeOnChangedInputs,
                            ),
                        )
                    }

            val preBuildTaskName = IosTaskType.PreBuildIosApp.getTaskName(module, platform)
            tasks.registerTask(
                task = IosPreBuildTask(
                    taskName = preBuildTaskName,
                    module = module,
                    buildType = BuildType.Debug,
                    platform = platform,
                    outputRoot = context.buildOutputRoot,
                ),
                dependsOn = buildList {
                    composeResourcesTaskName?.let(::add)
                    add(IosTaskType.Framework.getTaskName(module, platform, false, BuildType.Debug),)
                },
            )

            val buildTaskName = IosTaskType.BuildIosApp.getTaskName(module, platform)
            tasks.registerTask(
                task = IosBuildTask(
                    platform = platform,
                    module = module,
                    buildType = BuildType.Debug,
                    buildOutputRoot = context.buildOutputRoot,
                    taskOutputPath = context.getTaskOutputPath(buildTaskName),
                    taskName = buildTaskName,
                    isTest = false,
                ),
                dependsOn = listOf(
                    preBuildTaskName,
                    // This goes here instead of pre-build because if the build is run from xcode, then managing the
                    // project won't help much anyway.
                    ManageXCodeProjectTask.taskName(module),
                ),
            )

            val runTaskName = IosTaskType.RunIosApp.getTaskName(module, platform)
            tasks.registerTask(
                task = IosRunTask(
                    taskName = runTaskName,
                    platform = platform,
                    buildType = buildType,
                    module = module,
                    taskOutputPath = context.getTaskOutputPath(runTaskName),
                ),
                dependsOn = listOf(buildTaskName)
            )
        }

    allModules()
        .alsoPlatforms(Platform.IOS)
        .alsoTests()
        .filterModuleType { it == ProductType.IOS_APP }
        .filter { isComposeEnabledFor(it.module) }
        .withEach {
            // include local resources (self)
            includeComposeResources(
                from = module,
                into = module,
                forPlatform = platform,
            )

            // include resources from dependencies
            module.getModuleDependencies(
                isTest = isTest,
                platform = platform,
                dependencyReason = ResolutionScope.RUNTIME,
                userCacheRoot = context.userCacheRoot,
            ).filter(::isComposeEnabledFor).forEach { dependencyModule ->
                includeComposeResources(
                    from = dependencyModule,
                    into = module,
                    forPlatform = platform,
                )
            }
        }
}

private fun ProjectTasksBuilder.includeComposeResources(
    from: AmperModule,
    into: AmperModule,
    forPlatform: Platform,
) {
    from.fragmentsTargeting(
        platform = forPlatform,
        includeTestFragments = false,
    ).forEach { fragment ->
        tasks.registerDependency(
            taskName = IosTaskType.PrepareComposeResources.getTaskName(into, forPlatform),
            dependsOn = ComposeFragmentTaskType.ComposeResourcesPrepare.getTaskName(fragment),
        )
    }
}

internal enum class IosTaskType(override val prefix: String) : PlatformTaskType {
    Framework("framework"),
    BuildIosApp("buildIosApp"),
    RunIosApp("runIosApp"),
    PrepareComposeResources("prepareComposeResourcesForIos"),
    PreBuildIosApp("preBuildIosApp")
}
