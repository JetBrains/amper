/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.frontend.doCapitalize
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.tasks.ProjectTaskRegistrar
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.PublishTask

fun ProjectTaskRegistrar.setupJvmTasks() {
    onEachTaskType(Platform.JVM) { module, executeOnChangedInputs, platform, isTest ->
        val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
        val compileTaskName = CommonTaskType.Compile.getTaskName(module, platform, isTest)
        registerTask(
            JvmCompileTask(
                module = module,
                isTest = isTest,
                fragments = fragments,
                userCacheRoot = context.userCacheRoot,
                projectRoot = context.projectRoot,
                taskOutputRoot = context.getTaskOutputPath(compileTaskName),
                taskName = compileTaskName,
                executeOnChangedInputs = executeOnChangedInputs,
            ),
            CommonTaskType.Dependencies.getTaskName(module, platform, isTest)
        )

        registerTask(
            JvmRuntimeClasspathTask(
                module = module,
                isTest = isTest,
                taskName = CommonTaskType.RuntimeClasspath.getTaskName(module, platform, isTest),
            ),
            compileTaskName,
        )
    }

    onCompileModuleDependency(Platform.JVM) { module, dependsOn, _, platform, isTest ->
        registerDependency(
            CommonTaskType.Compile.getTaskName(module, platform, isTest),
            CommonTaskType.Compile.getTaskName(dependsOn, platform, false)
        )
    }

    onMain(Platform.JVM) { module, executeOnChangedInputs, platform, _ ->
        if (module.type.isApplication()) {
            registerTask(
                JvmRunTask(
                    module = module,
                    userCacheRoot = context.userCacheRoot,
                    projectRoot = context.projectRoot,
                    taskName = CommonTaskType.Run.getTaskName(module, platform),
                    commonRunSettings = context.commonRunSettings,
                    terminal = context.terminal,
                ),
                CommonTaskType.RuntimeClasspath.getTaskName(module, platform)
            )
        }
        val jarTaskName = CommonTaskType.Jar.getTaskName(module, platform)
        registerTask(
            JvmClassesJarTask(
                taskName = jarTaskName,
                module = module,
                taskOutputRoot = context.getTaskOutputPath(jarTaskName),
                executeOnChangedInputs = executeOnChangedInputs,
            ),
            CommonTaskType.Compile.getTaskName(module, platform, isTest = false),
        )

        val publishRepositories = module.mavenRepositories.filter { it.publish }
        for (repository in publishRepositories) {
            val publishTaskSuffix = "To${repository.id.doCapitalize()}"
            val publishTaskName = CommonTaskType.Publish.getTaskName(module, platform, suffix = publishTaskSuffix)
            registerTask(
                PublishTask(
                    taskName = publishTaskName,
                    module = module,
                    targetRepository = repository,
                    tempRoot = context.projectTempRoot,
                    mavenLocalRepository = context.mavenLocalRepository,
                ),
                jarTaskName,
            )

            // Publish task should depend on publishing of modules which this module depends on
            // TODO It could be optional in the future by, e.g., introducing an option to `publish` command
            val thisModuleFragments = module.fragments.filter { it.platforms.contains(platform) && !it.isTest }
            val thisModuleDependencies = thisModuleFragments.flatMap { it.externalDependencies }.filterIsInstance<PotatoModuleDependency>()
            for (moduleDependency in thisModuleDependencies) {
                val dependencyPublishTaskName = CommonTaskType.Publish.getTaskName(moduleDependency.module, platform, suffix = publishTaskSuffix)
                registerDependency(publishTaskName, dependencyPublishTaskName)
            }

            // TODO It should be optional to publish or not to publish sources
            val sourcesJarTaskName = CommonTaskType.SourcesJar.getTaskName(module, platform)
            registerDependency(publishTaskName, sourcesJarTaskName)
        }
    }

    onTest(Platform.JVM) { module, _, platform, _ ->
        val testTaskName = CommonTaskType.Test.getTaskName(module, platform)
        registerTask(
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

        registerDependency(
            taskName = CommonTaskType.Compile.getTaskName(module, platform, true),
            dependsOn = CommonTaskType.Compile.getTaskName(module, platform, false),
        )
    }
}
