/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isParentOf
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.tasks.ProjectTasksBuilder.Companion.testSuffix
import org.jetbrains.amper.tasks.android.setupAndroidTasks
import org.jetbrains.amper.tasks.compose.setupComposeTasks
import org.jetbrains.amper.tasks.custom.setupCustomTasks
import org.jetbrains.amper.tasks.ios.setupIosTasks
import org.jetbrains.amper.tasks.jvm.setupJvmTasks
import org.jetbrains.amper.tasks.ksp.setupKspTasks
import org.jetbrains.amper.tasks.native.setupNativeTasks
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import kotlin.io.path.div

internal interface TaskType {
    val prefix: String
}

internal interface PlatformTaskType : TaskType {
    fun getTaskName(
        module: AmperModule,
        platform: Platform,
        isTest: Boolean = false,
        buildType: BuildType? = null,
        suffix: String = "",
    ): TaskName {
        val uppercasePlatform = platform.pretty.replaceFirstChar { it.uppercase() }
        val buildTypeSuffix = buildType?.suffix(platform) ?: ""
        val testSuffix = isTest.testSuffix
        return TaskName.moduleTask(module, "$prefix$uppercasePlatform$testSuffix$buildTypeSuffix$suffix")
    }
}

internal interface FragmentTaskType : TaskType {
    fun getTaskName(fragment: Fragment): TaskName =
        TaskName.moduleTask(fragment.module, "$prefix${fragment.name.replaceFirstChar { it.uppercase() }}")
}

data class ModuleSequenceCtx(
    val module: AmperModule,
    val platform: Platform = Platform.COMMON,
    val isTest: Boolean = false,
    val buildType: BuildType = BuildType.Debug,
)

data class ModuleDependencySequenceCtx(
    val module: AmperModule,
    val dependencyReason: ResolutionScope,
    val dependsOn: AmperModule,
    // For decomposing declarations.
    val platform: Platform = Platform.COMMON,
    val isTest: Boolean = false,
    val buildType: BuildType = BuildType.Debug,
)

class ProjectTasksBuilder(
    val context: CliContext,
    val model: Model
) {
    val tasks = TaskGraphBuilder()
    val executeOnChangedInputs = ExecuteOnChangedInputs(context.buildOutputRoot)

    fun build(): TaskGraph {
        setupCommonTasks()
        setupJvmTasks()
        setupAndroidTasks()
        setupNativeTasks()
        setupIosTasks()
        setupKspTasks()
        setupComposeTasks()
        setupCustomTaskDependencies()
        setupCustomTasks()
        return tasks.build()
    }

    fun allFragments() = model.modules.asSequence().flatMap { it.fragments }

    fun allModules() = model.modules.asSequence().map { ModuleSequenceCtx(it) }

    fun Sequence<ModuleSequenceCtx>.alsoPlatforms(parent: Platform? = null) = flatMap { ctx ->
        ctx.module.leafPlatforms.filter { parent?.isParentOf(it) ?: true }.map { ctx.copy(platform = it) }
    }

    fun Sequence<ModuleSequenceCtx>.alsoTests() = flatMap {
        listOf(it.copy(isTest = false), it.copy(isTest = true))
    }

    fun Sequence<ModuleSequenceCtx>.alsoBuildTypes(buildTypes: List<BuildType> = BuildType.entries) =
        flatMap { ctx -> buildTypes.map { ctx.copy(buildType = it) } }

    fun Sequence<ModuleSequenceCtx>.filterModuleType(type: (ProductType) -> Boolean) =
        filter { type(it.module.type) }

    inline fun <T> Sequence<T>.withEach(block: T.() -> Unit) = forEach(block)

    fun Sequence<ModuleSequenceCtx>.selectModuleDependencies(
        dependencyReason: ResolutionScope,
    ): Sequence<ModuleDependencySequenceCtx> = flatMap { ctx ->
        ctx.module.getModuleDependencies(
            ctx.isTest,
            ctx.platform,
            dependencyReason,
            context.userCacheRoot
        ).map {
            ModuleDependencySequenceCtx(
                module = ctx.module,
                dependencyReason = dependencyReason,
                dependsOn = it,
                platform = ctx.platform,
                isTest = ctx.isTest,
                buildType = ctx.buildType,
            )
        }
    }

    companion object {

        internal val Boolean.testSuffix: String
            get() = if (this) "Test" else ""

        /**
         * The method is public, but a caveat applies: a task should not know about an output path of other tasks.
         * All interaction between tasks should be around passing typed value in TaskResult inheritor,
         * task properties, or module properties
         */
        fun CliContext.getTaskOutputPath(taskName: TaskName): TaskOutputRoot =
            TaskOutputRoot(path = buildOutputRoot.path / "tasks" / taskName.name.replace(":", "_"))
    }
}
