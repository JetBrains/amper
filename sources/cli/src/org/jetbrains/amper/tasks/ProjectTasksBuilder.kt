/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.ModuleTasksPart
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.allSourceFragmentCompileDependencies
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.testSuffix
import org.jetbrains.amper.tasks.android.setupAndroidTasks
import org.jetbrains.amper.tasks.ios.setupIosTasks
import org.jetbrains.amper.tasks.jvm.setupJvmTasks
import org.jetbrains.amper.tasks.native.setupNativeTasks
import org.jetbrains.amper.util.BuildType
import kotlin.io.path.div

internal interface TaskType {
    val prefix: String
}

internal interface PlatformTaskType : TaskType {

    fun getTaskName(module: PotatoModule, platform: Platform, isTest: Boolean = false, buildType: BuildType? = null, suffix: String = ""): TaskName =
        TaskName.fromHierarchy(
            listOf(
                module.userReadableName,
                "$prefix${platform.pretty.replaceFirstChar { it.uppercase() }}${isTest.testSuffix}${buildType?.suffix(platform) ?: ""}$suffix"
            )
        )
}

internal interface FragmentTaskType : TaskType {

    fun getTaskName(fragment: Fragment): TaskName =
        TaskName.fromHierarchy(fragment.module.userReadableName, "$prefix${fragment.name.replaceFirstChar { it.uppercase() }}")
}

class ProjectTasksBuilder(private val context: ProjectContext, private val model: Model) {
    fun build(): TaskGraph {
        val builder = ProjectTaskRegistrar(context, model)
        builder.setupCommonTasks()
        builder.setupJvmTasks()
        builder.setupAndroidTasks()
        builder.setupNativeTasks()
        builder.setupIosTasks()
        builder.setupCustomTaskDependencies()
        return builder.build()
    }

    private fun ProjectTaskRegistrar.setupCommonTasks() {
        onEachTaskType { module, executeOnChangedInputs, platform, isTest ->
            val fragmentsIncludeProduction = module.fragmentsTargeting(platform, includeTestFragments = isTest)
            val fragmentsCompileModuleDependencies = module.fragmentsModuleDependencies(isTest, platform, DependencyReason.Compile)
            registerTask(
                ResolveExternalDependenciesTask(
                    module,
                    context.userCacheRoot,
                    executeOnChangedInputs,
                    platform = platform,
                    // for test code, we resolve dependencies on union of test and prod dependencies
                    fragments = fragmentsIncludeProduction,
                    fragmentsCompileModuleDependencies = fragmentsCompileModuleDependencies,
                    taskName = CommonTaskType.Dependencies.getTaskName(module, platform, isTest)
                )
            )
        }
        onEachFragment { module, executeOnChangedInputs, fragment ->
            val taskName = CommonFragmentTaskType.CompileMetadata.getTaskName(fragment)
            registerTask(
                MetadataCompileTask(
                    taskName = taskName,
                    module = module,
                    fragment = fragment,
                    userCacheRoot = context.userCacheRoot,
                    terminal = context.terminal,
                    taskOutputRoot = context.getTaskOutputPath(taskName),
                    executeOnChangedInputs = executeOnChangedInputs,
                )
            )
            // TODO make dependency resolution a module-wide task instead (when contexts support sets of platforms)
            fragment.platforms.forEach { leafPlatform ->
                registerDependency(
                    taskName = taskName,
                    dependsOn = CommonTaskType.Dependencies.getTaskName(module, leafPlatform)
                )
            }

            fragment.allSourceFragmentCompileDependencies.forEach { otherFragment ->
                registerDependency(
                    taskName = taskName,
                    dependsOn = CommonFragmentTaskType.CompileMetadata.getTaskName(otherFragment)
                )
            }
        }
        onEachLeafPlatform { module, executeOnChangedInputs, platform ->
            val sourcesJarTaskName = CommonTaskType.SourcesJar.getTaskName(module, platform)
            registerTask(
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

    private fun ProjectTaskRegistrar.setupCustomTaskDependencies() {
        onEachModule { module, _ ->
            val tasksSettings = module.parts.filterIsInstance<ModuleTasksPart>().singleOrNull() ?: return@onEachModule
            for ((taskName, taskSettings) in tasksSettings.settings) {
                val thisModuleTaskName = TaskName.fromHierarchy(module.userReadableName, taskName)

                for (dependsOnTaskName in taskSettings.dependsOn) {
                    val dependsOnTask = if (dependsOnTaskName.startsWith(":")) {
                        TaskName(dependsOnTaskName)
                    } else {
                        TaskName.fromHierarchy(module.userReadableName, dependsOnTaskName)
                    }

                    registerDependency(thisModuleTaskName, dependsOnTask)
                }
            }
        }
    }

    companion object {
        internal val Boolean.testSuffix: String
            get() = if (this) "Test" else ""

        internal enum class CommonTaskType(override val prefix: String) : PlatformTaskType {
            Compile("compile"),
            Dependencies("resolveDependencies"),
            Jar("jar"),
            SourcesJar("sourcesJar"),
            Publish("publish"),
            Run("run"),
            Test("test"),
        }

        internal enum class CommonFragmentTaskType(override val prefix: String) : FragmentTaskType {
            CompileMetadata("compileMetadata"),
        }

        internal fun ProjectContext.getTaskOutputPath(taskName: TaskName): TaskOutputRoot =
            TaskOutputRoot(path = buildOutputRoot.path / "tasks" / taskName.name.replace(":", "_"))
    }
}
