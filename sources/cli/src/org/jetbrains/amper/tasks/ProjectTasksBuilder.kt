/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.testSuffix
import org.jetbrains.amper.tasks.android.setupAndroidTasks
import org.jetbrains.amper.tasks.jvm.setupJvmTasks
import org.jetbrains.amper.tasks.native.setupNativeTasks
import org.jetbrains.amper.util.BuildType
import kotlin.io.path.div

internal interface TaskType {
    val prefix: String

    fun getTaskName(module: PotatoModule, platform: Platform, isTest: Boolean = false, buildType: BuildType? = null): TaskName =
        TaskName.fromHierarchy(
            listOf(
                module.userReadableName,
                "$prefix${platform.pretty.replaceFirstChar { it.uppercase() }}${isTest.testSuffix}${buildType?.suffix(platform) ?: ""}"
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
        return builder.build()
    }

    private fun ProjectTaskRegistrar.setupCommonTasks() {
        onTaskType { module, executeOnChangedInputs, platform, isTest ->
            val fragmentsIncludeProduction = module.fragmentsIncludeProduction(isTest, platform)
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
    }

    companion object {
        internal val Boolean.testSuffix: String
            get() = if (this) "Test" else ""

        internal enum class CommonTaskType(override val prefix: String) : TaskType {
            Compile("compile"),
            Dependencies("resolveDependencies"),
            Run("run"),
            Test("test"),
        }

        internal fun ProjectContext.getTaskOutputPath(taskName: TaskName): TaskOutputRoot =
            TaskOutputRoot(path = buildOutputRoot.path / "tasks" / taskName.name.replace(":", "_"))
    }
}
