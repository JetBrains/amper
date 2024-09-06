/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.frontend.doCapitalize
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.PublishTask

fun ProjectTasksBuilder.setupJvmTasks() {
    allModules()
        .alsoPlatforms(Platform.JVM)
        .alsoTests()
        .withEach {
            val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
            val compileTaskName = CommonTaskType.Compile.getTaskName(module, platform, isTest)
            tasks.registerTask(
                JvmCompileTask(
                    module = module,
                    isTest = isTest,
                    fragments = fragments,
                    userCacheRoot = context.userCacheRoot,
                    projectRoot = context.projectRoot,
                    taskOutputRoot = context.getTaskOutputPath(compileTaskName),
                    taskName = compileTaskName,
                    executeOnChangedInputs = executeOnChangedInputs,
                    tempRoot = context.projectTempRoot,
                ),
                CommonTaskType.Dependencies.getTaskName(module, platform, isTest)
            )

            if (!isTest) {
            // We do not pack test classes into a jar.
            val jarTaskName = CommonTaskType.Jar.getTaskName(module, platform, isTest = false)
            tasks.registerTask(
                JvmClassesJarTask(
                    taskName = jarTaskName,
                    module = module,
                    isTest = false,
                    taskOutputRoot = context.getTaskOutputPath(jarTaskName),
                    executeOnChangedInputs = executeOnChangedInputs,
                ),
                CommonTaskType.Compile.getTaskName(module, platform, isTest= false),
            )
            }

            val runtimeClasspathTaskName = CommonTaskType.RuntimeClasspath.getTaskName(module, platform, isTest)
            tasks.registerTask(
                JvmRuntimeClasspathTask(
                    module = module,
                    isTest = isTest,
                    taskName = runtimeClasspathTaskName,
                ),
                listOf(
                    if (isTest) {
                    CommonTaskType.Compile.getTaskName(module, platform, isTest = true)
                } else {
                    CommonTaskType.Jar.getTaskName(module, platform, isTest = false)
                },
                    CommonTaskType.Dependencies.getTaskName(module, platform, isTest),
                )
            )

            if (isTest) {
                // test runtime classpath should include both test and prod jars
                tasks.registerDependency(
                    runtimeClasspathTaskName,
                    CommonTaskType.Jar.getTaskName(module, platform, isTest = false)
                )
            }

            // custom task roots
            module.customTasks.forEach { customTask ->
                customTask.addToModuleRootsFromCustomTask.forEach { add ->
                    if (platform.pathToParent.contains(add.platform) && isTest == add.isTest) {
                        tasks.registerDependency(compileTaskName, dependsOn = customTask.name)
                    }
                }
            }
        }

    allModules()
        .alsoPlatforms(Platform.JVM)
        .alsoTests()
        .selectModuleDependencies(ResolutionScope.COMPILE) {
            tasks.registerDependency(
                CommonTaskType.Compile.getTaskName(module, platform, isTest),
                CommonTaskType.Compile.getTaskName(dependsOn, platform, false)
            )
        }

    allModules()
        .alsoPlatforms(Platform.JVM)
        .alsoTests()
        .selectModuleDependencies(ResolutionScope.RUNTIME) {
            tasks.registerDependency(
                CommonTaskType.RuntimeClasspath.getTaskName(module, platform, isTest),
                CommonTaskType.Jar.getTaskName(dependsOn, platform, false)
            )
        }

    allModules()
        .alsoPlatforms(Platform.JVM)
        .withEach {
            if (module.type.isApplication()) {
                tasks.registerTask(
                    JvmRunTask(
                        module = module,
                        userCacheRoot = context.userCacheRoot,
                        projectRoot = context.projectRoot,
                        taskName = CommonTaskType.Run.getTaskName(module, platform),
                        commonRunSettings = context.commonRunSettings,
                        terminal = context.terminal,
                        tempRoot = context.projectTempRoot,
                    ),
                    CommonTaskType.RuntimeClasspath.getTaskName(module, platform),
                )
            }

            val publishRepositories = module.mavenRepositories.filter { it.publish }
            for (repository in publishRepositories) {
                val publishTaskSuffix = "To${repository.id.doCapitalize()}"
                val publishTaskName = CommonTaskType.Publish.getTaskName(module, platform, suffix = publishTaskSuffix)
                tasks.registerTask(
                    PublishTask(
                        taskName = publishTaskName,
                        module = module,
                        targetRepository = repository,
                        tempRoot = context.projectTempRoot,
                        mavenLocalRepository = context.mavenLocalRepository,
                    ),
                    CommonTaskType.Jar.getTaskName(module, platform, isTest = false),
                )

                // Publish task should depend on publishing of modules which this module depends on
                // TODO It could be optional in the future by, e.g., introducing an option to `publish` command
                // TODO Why !isTest when there should be no iteration over tests?
                val thisModuleFragments = module.fragments.filter { it.platforms.contains(platform) && !isTest }
                val thisModuleDependencies =
                    thisModuleFragments.flatMap { it.externalDependencies }.filterIsInstance<PotatoModuleDependency>()
                for (moduleDependency in thisModuleDependencies) {
                    val dependencyPublishTaskName =
                        CommonTaskType.Publish.getTaskName(
                            moduleDependency.module,
                            platform,
                            suffix = publishTaskSuffix
                        )
                    tasks.registerDependency(publishTaskName, dependencyPublishTaskName)
                }

                // TODO It should be optional to publish or not to publish sources
                val sourcesJarTaskName = CommonTaskType.SourcesJar.getTaskName(module, platform)
                tasks.registerDependency(publishTaskName, sourcesJarTaskName)

                module.customTasks.forEach { customTask ->
                    if (customTask.publishArtifacts.isNotEmpty()) {
                        tasks.registerDependency(publishTaskName, dependsOn = customTask.name)
                    }
                }
            }
        }

    allModules()
        .alsoPlatforms(Platform.JVM)
        .withEach {
            val testTaskName = CommonTaskType.Test.getTaskName(module, platform)
            tasks.registerTask(
                JvmTestTask(
                    module = module,
                    userCacheRoot = context.userCacheRoot,
                    projectRoot = context.projectRoot,
                    tempRoot = context.projectTempRoot,
                    taskName = testTaskName,
                    taskOutputRoot = context.getTaskOutputPath(testTaskName),
                    terminal = context.terminal,
                ),
                listOf(
                    CommonTaskType.Compile.getTaskName(module, platform, true),
                    CommonTaskType.RuntimeClasspath.getTaskName(module, platform, true),
                )
            )

            tasks.registerDependency(
                taskName = CommonTaskType.Compile.getTaskName(module, platform, true),
                dependsOn = CommonTaskType.Compile.getTaskName(module, platform, false),
            )
        }
}
