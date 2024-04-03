/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.testSuffix
import org.jetbrains.amper.tasks.android.setupAndroidTasks
import org.jetbrains.amper.tasks.ios.setupIosTasks
import org.jetbrains.amper.tasks.jvm.setupJvmTasks
import org.jetbrains.amper.tasks.native.setupNativeTasks
import org.jetbrains.amper.util.BuildType
import kotlin.io.path.div

internal interface TaskType {
    val prefix: String

    fun getTaskName(module: PotatoModule, platform: Platform, isTest: Boolean = false, buildType: BuildType? = null, suffix: String = ""): TaskName =
        TaskName.fromHierarchy(
            listOf(
                module.userReadableName,
                "$prefix${platform.pretty.replaceFirstChar { it.uppercase() }}${isTest.testSuffix}${buildType?.suffix(platform) ?: ""}$suffix"
            )
        )
}

class ProjectTasksBuilder(private val context: ProjectContext, private val model: Model) {
    fun build(): TaskGraph {
        val builder = ProjectTaskRegistrar(context, model)
        builder.setupCommonTasks()
        builder.setupJvmTasks()
        builder.setupAndroidTasks()
        builder.setupNativeTasks()
        builder.setupIosTasks()
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
        onEachPlatform { module, executeOnChangedInputs, platform ->
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

    companion object {
        internal val Boolean.testSuffix: String
            get() = if (this) "Test" else ""

        internal enum class CommonTaskType(override val prefix: String) : TaskType {
            Compile("compile"),
            Dependencies("resolveDependencies"),
            Jar("jar"),
            SourcesJar("sourcesJar"),
            Publish("publish"),
            Run("run"),
            Test("test"),
        }

        internal fun ProjectContext.getTaskOutputPath(taskName: TaskName): TaskOutputRoot =
            TaskOutputRoot(path = buildOutputRoot.path / "tasks" / taskName.name.replace(":", "_"))
    }
}
