/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.jvm

import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.doCapitalize
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.frontend.schema.DependencyMode
import org.jetbrains.amper.frontend.schema.enabled
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.FragmentTaskType
import org.jetbrains.amper.tasks.PlatformTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.getTaskOutputPath
import org.jetbrains.amper.tasks.PublishTask
import org.jetbrains.amper.tasks.compose.isComposeEnabledFor
import org.jetbrains.amper.tasks.compose.isHotReloadEnabledFor
import org.jetbrains.amper.tasks.getModuleDependencies

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
                    buildOutputRoot = context.buildOutputRoot,
                ),
                dependsOn = buildList {
                    add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))
                    if (isTest) {
                        // test compilation depends on main classes
                        add(CommonTaskType.Compile.getTaskName(module, platform, isTest = false))
                    }

                    if (fragments.any { it.settings.java.annotationProcessing.enabled }) {
                        add(JvmSpecificTaskType.JavaAnnotationProcessorClasspath.getTaskName(module, platform, isTest))
                    }

                    module.getModuleDependencies(isTest, platform, ResolutionScope.COMPILE, context.userCacheRoot)
                        .forEach {
                            add(CommonTaskType.Compile.getTaskName(it, platform, isTest = false))
                        }
                }
            )

            val runtimeClasspathTaskName = CommonTaskType.RuntimeClasspath.getTaskName(module, platform, isTest)

            val runtimeClasspathMode = fragments.firstOrNull { Platform.COMMON in it.platforms }
                ?.settings
                ?.jvm
                ?.runtimeClasspathMode ?: DependencyMode.JARS

            tasks.registerTask(
                task = JvmRuntimeClasspathTask(
                    module = module,
                    isTest = isTest,
                    taskName = runtimeClasspathTaskName,
                ),
                dependsOn = buildList {
                    if (isTest) {
                        add(CommonTaskType.Compile.getTaskName(module, platform, isTest = true))
                    }
                    val mode = when (runtimeClasspathMode) {
                        DependencyMode.JARS -> CommonTaskType.Jar
                        DependencyMode.CLASSES -> CommonTaskType.Classes
                    }
                    add(mode.getTaskName(module, platform, isTest = false))
                    add(CommonTaskType.Dependencies.getTaskName(module, platform, isTest))

                    module.getModuleDependencies(isTest, platform, ResolutionScope.RUNTIME, context.userCacheRoot)
                        .forEach { add(mode.getTaskName(it, platform, isTest = false)) }
                }
            )

            if (!isTest) {
                // We do not pack test classes into a jar.
                val jarTaskName = CommonTaskType.Jar.getTaskName(module, platform, isTest = false)
                tasks.registerTask(
                    JvmClassesJarTask(
                        taskName = jarTaskName,
                        module = module,
                        taskOutputRoot = context.getTaskOutputPath(jarTaskName),
                        executeOnChangedInputs = executeOnChangedInputs,
                    ),
                    CommonTaskType.Compile.getTaskName(module, platform, isTest = false),
                )

                val classesTaskName = CommonTaskType.Classes.getTaskName(module, platform, isTest = false)
                tasks.registerTask(
                    JvmClassesTask(classesTaskName),
                    CommonTaskType.Compile.getTaskName(module, platform, isTest = false)
                )

                if (isComposeEnabledFor(module)) {
                    if (isHotReloadEnabledFor(module)) {
                        val reloadTaskName = HotReloadTaskType.Reload.getTaskName(module, platform, isTest = false)
                        tasks.registerTask(
                            JvmReloadClassesTask(reloadTaskName),
                            dependsOn = buildList {
                                add(CommonTaskType.Compile.getTaskName(module, platform, isTest = false))
                                module.getModuleDependencies(
                                    isTest = false,
                                    platform,
                                    ResolutionScope.RUNTIME,
                                    context.userCacheRoot
                                )
                                    .forEach {
                                        add(CommonTaskType.Compile.getTaskName(it, platform, isTest = false))
                                    }
                            }
                        )
                    }

                    fragments.forEach { fragment ->
                        tasks.registerDependency(
                            taskName = CommonTaskType.Compile.getTaskName(module, platform, isTest = false),
                            dependsOn = JvmFragmentTaskType.PrepareComposeResources.getTaskName(fragment),
                        )
                    }
                }
            }

            // custom task roots
            // TODO: Remove once fully migrated to artifacts
            module.customTasks.forEach { customTask ->
                customTask.addToModuleRootsFromCustomTask.forEach { add ->
                    if (platform.pathToParent.contains(add.platform) && isTest == add.isTest) {
                        tasks.registerDependency(compileTaskName, dependsOn = customTask.name)
                    }
                }
            }
        }

    allModules().alsoPlatforms(Platform.JVM).withEach {
        if (isComposeEnabledFor(module)) {
            module.fragments.filter { Platform.JVM in it.platforms }.forEach { fragment ->
                val prepareForJvm = JvmFragmentTaskType.PrepareComposeResources.getTaskName(fragment)
                tasks.registerTask(
                    task = JvmComposeResourcesTask(
                        taskName = prepareForJvm,
                        fragment = fragment,
                        taskOutputRoot = context.getTaskOutputPath(prepareForJvm),
                        executeOnChangedInputs = executeOnChangedInputs,
                    ),
                )
            }
        }
    }

    allModules()
        .alsoPlatforms(Platform.JVM)
        .withEach {
            if (isComposeEnabledFor(module) && isHotReloadEnabledFor(module)) {
                tasks.registerTask(
                    JvmHotRunTask(
                        module = module,
                        userCacheRoot = context.userCacheRoot,
                        projectRoot = context.projectRoot,
                        taskName = HotReloadTaskType.HotRun.getTaskName(module, platform),
                        commonRunSettings = context.commonRunSettings,
                        terminal = context.terminal,
                        tempRoot = context.projectTempRoot,
                        executeOnChangedInputs = executeOnChangedInputs,
                    ),
                    CommonTaskType.RuntimeClasspathClasses.getTaskName(module, platform),
                )
            }

            if (module.type.isApplication()) {
                if (!(isComposeEnabledFor(module) && isHotReloadEnabledFor(module))) {
                    tasks.registerTask(
                        JvmRunTask(
                            module = module,
                            userCacheRoot = context.userCacheRoot,
                            projectRoot = context.projectRoot,
                            taskName = CommonTaskType.Run.getTaskName(module, platform),
                            commonRunSettings = context.commonRunSettings,
                            terminal = context.terminal,
                            tempRoot = context.projectTempRoot,
                            executeOnChangedInputs = executeOnChangedInputs,
                        ),
                        CommonTaskType.RuntimeClasspath.getTaskName(module, platform),
                    )
                }

                val executableJarTaskName = JvmSpecificTaskType.ExecutableJar.getTaskName(module, platform)
                tasks.registerTask(
                    ExecutableJarTask(
                        taskName = executableJarTaskName,
                        module = module,
                        executeOnChangedInputs = executeOnChangedInputs,
                        userCacheRoot = context.userCacheRoot,
                        taskOutputRoot = context.getTaskOutputPath(executableJarTaskName),
                    ),
                    dependsOn = buildList {
                        add(CommonTaskType.Compile.getTaskName(module, platform, isTest = false))
                        add(CommonTaskType.RuntimeClasspath.getTaskName(module, platform, isTest = false))
                    }
                )

                // Register a task to run the executable jar
                val executableJarRunTaskName = JvmSpecificTaskType.RunExecutableJar.getTaskName(module, platform)
                tasks.registerTask(
                    ExecutableJarRunTask(
                        taskName = executableJarRunTaskName,
                        module = module, 
                        userCacheRoot = context.userCacheRoot,
                        projectRoot = context.projectRoot,
                        tempRoot = context.projectTempRoot,
                        terminal = context.terminal,
                        commonRunSettings = context.commonRunSettings,
                    ),
                    dependsOn = listOf(executableJarTaskName)
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
                    ),
                    dependsOn = listOf(
                        CommonTaskType.Jar.getTaskName(module, platform, isTest = false),
                        // we need dependencies to get publication coordinate overrides (e.g. -jvm variant)
                        CommonTaskType.Dependencies.getTaskName(module, platform, isTest = false),
                    )
                )

                // Publish task should depend on publishing of modules which this module depends on
                // TODO It could be optional in the future by, e.g., introducing an option to `publish` command
                val thisModuleFragments = module.fragments.filter { it.platforms.contains(platform) && !it.isTest }
                val thisModuleDependencies =
                    thisModuleFragments.flatMap { it.externalDependencies }.filterIsInstance<LocalModuleDependency>()
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
                    buildOutputRoot = context.buildOutputRoot,
                    taskName = testTaskName,
                    taskOutputRoot = context.getTaskOutputPath(testTaskName),
                    terminal = context.terminal,
                    commonRunSettings = context.commonRunSettings,
                    executeOnChangedInputs = executeOnChangedInputs,
                ),
                listOf(
                    CommonTaskType.Compile.getTaskName(module, platform, true),
                    CommonTaskType.RuntimeClasspath.getTaskName(module, platform, true),
                )
            )
        }
}

private enum class JvmFragmentTaskType(
    override val prefix: String,
) : FragmentTaskType {
    PrepareComposeResources("prepareComposeResourcesForJvm"),
    ;
}

internal enum class HotReloadTaskType(override val prefix: String) : PlatformTaskType {
    Reload("reload"),
    HotRun("hotRun"),
}

// Adding a specific task type for JVM-specific tasks
internal enum class JvmSpecificTaskType(override val prefix: String) : PlatformTaskType {
    ExecutableJar("executableJar"),
    RunExecutableJar("runExecutableJar"),
    JavaAnnotationProcessorDependencies("resolveJavaAnnotationProcessorDependencies"),
    JavaAnnotationProcessorClasspath("javaAnnotationProcessorClasspath"),
}
