/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModuleTasksPart
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.allSourceFragmentCompileDependencies
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.testSuffix
import org.jetbrains.amper.tasks.android.setupAndroidTasks
import org.jetbrains.amper.tasks.custom.setupCustomTasks
import org.jetbrains.amper.tasks.ios.setupIosTasks
import org.jetbrains.amper.tasks.jvm.setupJvmTasks
import org.jetbrains.amper.tasks.ksp.setupKspTasks
import org.jetbrains.amper.tasks.native.setupNativeTasks
import org.jetbrains.amper.util.BuildType
import kotlin.io.path.div

internal interface TaskType {
    val prefix: String
}

internal interface PlatformTaskType : TaskType {

    fun getTaskName(
        module: PotatoModule,
        platform: Platform,
        isTest: Boolean = false,
        buildType: BuildType? = null,
        suffix: String = ""
    ): TaskName =
        TaskName.moduleTask(
            module,
            "$prefix${platform.pretty.replaceFirstChar { it.uppercase() }}${isTest.testSuffix}${
                buildType?.suffix(
                    platform
                ) ?: ""
            }$suffix",
        )
}

internal interface FragmentTaskType : TaskType {

    fun getTaskName(fragment: Fragment): TaskName =
        TaskName.moduleTask(fragment.module, "$prefix${fragment.name.replaceFirstChar { it.uppercase() }}")
}

class ProjectTasksBuilder(private val context: CliContext, private val model: Model) {
    fun build(): TaskGraph {
        val builder = TaskGraphBuilderCtx(context, model)
        builder.setupCommonTasks()
        builder.setupJvmTasks()
        builder.setupAndroidTasks()
        builder.setupNativeTasks()
        builder.setupIosTasks()
        builder.setupKspTasks()
        builder.setupCustomTaskDependencies()
        builder.setupCustomTasks()
        return builder.build()
    }

    /**
     * Returns all fragments in this module that target the given [platform].
     * If [includeTestFragments] is false, only production fragments are returned.
     */
    private fun PotatoModule.fragmentsTargeting(platform: Platform, includeTestFragments: Boolean): List<Fragment> =
        fragments.filter { (includeTestFragments || !it.isTest) && it.platforms.contains(platform) }

    private fun TaskGraphBuilderCtx.setupCommonTasks() {
        allModules()
            .alsoPlatforms()
            .alsoTests()
            .withEach {
                val fragmentsIncludeProduction = module.fragmentsTargeting(platform, includeTestFragments = isTest)
                val fragmentsCompileModuleDependencies =
                    module.buildDependenciesGraph(isTest, platform, ResolutionScope.COMPILE, context.userCacheRoot)
                val fragmentsRuntimeModuleDependencies = when {
                    platform.isDescendantOf(Platform.NATIVE) -> null  // native world doesn't distinguish compile/runtime classpath
                    else -> module.buildDependenciesGraph(
                        isTest,
                        platform,
                        ResolutionScope.RUNTIME,
                        context.userCacheRoot
                    )
                }
                tasks.registerTask(
                    ResolveExternalDependenciesTask(
                        module,
                        context.userCacheRoot,
                        executeOnChangedInputs,
                        platform = platform,
                        // for test code, we resolve dependencies on union of test and prod dependencies
                        fragments = fragmentsIncludeProduction,
                        fragmentsCompileModuleDependencies = fragmentsCompileModuleDependencies,
                        fragmentsRuntimeModuleDependencies = fragmentsRuntimeModuleDependencies,
                        taskName = CommonTaskType.Dependencies.getTaskName(module, platform, isTest)
                    )
                )
            }

        allFragments().forEach {
            val taskName = CommonFragmentTaskType.CompileMetadata.getTaskName(it)
            tasks.registerTask(
                MetadataCompileTask(
                    taskName = taskName,
                    module = it.module,
                    fragment = it,
                    userCacheRoot = context.userCacheRoot,
                    taskOutputRoot = context.getTaskOutputPath(taskName),
                    executeOnChangedInputs = executeOnChangedInputs,
                    tempRoot = context.projectTempRoot,
                )
            )
            // TODO make dependency resolution a module-wide task instead (when contexts support sets of platforms)
            it.platforms.forEach { leafPlatform ->
                tasks.registerDependency(
                    taskName = taskName,
                    dependsOn = CommonTaskType.Dependencies.getTaskName(it.module, leafPlatform)
                )
            }

            it.allSourceFragmentCompileDependencies.forEach { otherFragment ->
                tasks.registerDependency(
                    taskName = taskName,
                    dependsOn = CommonFragmentTaskType.CompileMetadata.getTaskName(otherFragment)
                )
            }
        }

        allModules()
            .alsoPlatforms()
            .withEach {
                val module = module
                val sourcesJarTaskName = CommonTaskType.SourcesJar.getTaskName(module, platform)
                tasks.registerTask(
                    SourcesJarTask(
                        taskName = sourcesJarTaskName,
                        module = module,
                        platform = platform,
                        taskOutputRoot = context.getTaskOutputPath(sourcesJarTaskName),
                        executeOnChangedInputs = executeOnChangedInputs,
                    )
                )
            }
    }

    private fun TaskGraphBuilderCtx.setupCustomTaskDependencies() {
        allModules().withEach {
            val tasksSettings = module.parts.filterIsInstance<ModuleTasksPart>().singleOrNull() ?: return@withEach
            for ((taskName, taskSettings) in tasksSettings.settings) {
                val thisModuleTaskName = TaskName.moduleTask(module, taskName)

                for (dependsOnTaskName in taskSettings.dependsOn) {
                    val dependsOnTask = if (dependsOnTaskName.startsWith(":")) {
                        TaskName(dependsOnTaskName)
                    } else {
                        TaskName.moduleTask(module, dependsOnTaskName)
                    }

                    tasks.registerDependency(thisModuleTaskName, dependsOnTask)
                }
            }
        }
    }

    companion object {
        internal val Boolean.testSuffix: String
            get() = if (this) "Test" else ""

        internal enum class CommonTaskType(override val prefix: String) : PlatformTaskType {
            Compile("compile"),
            Ksp("ksp"),
            Dependencies("resolveDependencies"),
            TransformDependencies("transformDependencies"),
            Jar("jar"),
            SourcesJar("sourcesJar"),
            Publish("publish"),
            Run("run"),
            RuntimeClasspath("runtimeClasspath"),
            Test("test"),
        }

        internal enum class CommonFragmentTaskType(override val prefix: String) : FragmentTaskType {
            CompileMetadata("compileMetadata"),
        }

        /**
         * The method is public, but a caveat applies: a task should not know about an output path of other tasks.
         * All interaction between tasks should be around passing typed value in TaskResult inheritor,
         * task properties, or module properties
         */
        fun CliContext.getTaskOutputPath(taskName: TaskName): TaskOutputRoot =
            TaskOutputRoot(path = buildOutputRoot.path / "tasks" / taskName.name.replace(":", "_"))
    }
}
