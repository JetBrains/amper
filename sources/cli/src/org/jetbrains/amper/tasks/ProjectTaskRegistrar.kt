/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.dr.resolver.flow.toResolutionPlatform
import org.jetbrains.amper.frontend.dr.resolver.moduleDependenciesResolver
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.resolver.getCliDefaultFileCacheBuilder
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.targetLeafPlatforms

typealias OnModelBlock = TaskGraphBuilder.(model: Model, executeOnChangedInputs: ExecuteOnChangedInputs) -> Unit
typealias OnModuleBlock = TaskGraphBuilder.(module: PotatoModule, executeOnChangedInputs: ExecuteOnChangedInputs) -> Unit
typealias OnFragmentBlock = TaskGraphBuilder.(module: PotatoModule, executeOnChangedInputs: ExecuteOnChangedInputs, fragment: Fragment) -> Unit
typealias OnPlatformBlock = TaskGraphBuilder.(module: PotatoModule, executeOnChangedInputs: ExecuteOnChangedInputs, platform: Platform) -> Unit
typealias OnTaskTypeBlock = TaskGraphBuilder.(module: PotatoModule, executeOnChangedInputs: ExecuteOnChangedInputs, platform: Platform, isTest: Boolean) -> Unit
typealias OnBuildTypeBlock = TaskGraphBuilder.(module: PotatoModule, executeOnChangedInputs: ExecuteOnChangedInputs, platform: Platform, isTest: Boolean, buildType: BuildType) -> Unit
typealias OnCustomTaskBlock = TaskGraphBuilder.(customTaskDescription: CustomTaskDescription, executeOnChangedInputs: ExecuteOnChangedInputs) -> Unit

typealias OnDependencyPrecondition = (dependencyReason: DependencyReason) -> Boolean
typealias OnTaskTypeDependencyBlock = TaskGraphBuilder.(module: PotatoModule, dependsOn: PotatoModule, dependencyReason: DependencyReason, platform: Platform, isTest: Boolean) -> Unit
typealias OnBuildTypeDependencyBlock = TaskGraphBuilder.(module: PotatoModule, dependsOn: PotatoModule, dependencyReason: DependencyReason, platform: Platform, isTest: Boolean, buildType: BuildType) -> Unit

enum class DependencyReason {
    Compile, Runtime
}

class ProjectTaskRegistrar(val context: CliContext, private val model: Model) {
    private val onModel: MutableList<OnModelBlock> = mutableListOf()
    private val onModule: MutableList<OnModuleBlock> = mutableListOf()
    private val onFragment: MutableList<OnFragmentBlock> = mutableListOf()
    private val onPlatform: MutableList<OnPlatformBlock> = mutableListOf()
    private val onTaskType: MutableList<OnTaskTypeBlock> = mutableListOf()
    private val onBuildType: MutableList<OnBuildTypeBlock> = mutableListOf()
    private val onCustomTask: MutableList<OnCustomTaskBlock> = mutableListOf()

    fun build(): TaskGraph {
        val sortedByPath = model.modules.sortedBy { (it.source as PotatoModuleFileSource).buildFile }
        val tasks = TaskGraphBuilder()
        val executeOnChangedInputs = ExecuteOnChangedInputs(context.buildOutputRoot)

        onModel.forEach { it(tasks, model, executeOnChangedInputs) }
        for (module in sortedByPath) {
            onModule.forEach { it(tasks, module, executeOnChangedInputs) }

            for (fragment in module.fragments) {
                onFragment.forEach{ it(tasks, module, executeOnChangedInputs, fragment) }
            }

            for (platform in module.targetLeafPlatforms) {
                onPlatform.forEach { it(tasks, module, executeOnChangedInputs, platform) }

                for (isTest in listOf(false, true)) {
                    onTaskType.forEach { it(tasks, module, executeOnChangedInputs, platform, isTest) }

                    for (buildType in listOf(BuildType.Debug, BuildType.Release)) {
                        onBuildType.forEach { it(tasks, module, executeOnChangedInputs, platform, isTest, buildType) }
                    }
                }
            }

            module.customTasks.forEach { customTask ->
                onCustomTask.forEach { block ->
                    block(tasks, customTask, executeOnChangedInputs)
                }
            }
        }

        return tasks.build()
    }

    /**
     * Called once for the whole model.
     */
    fun forWholeModel(block: OnModelBlock) {
        onModel.add(block)
    }

    /**
     * Called once for each module.
     */
    fun onEachModule(block: OnModuleBlock) {
        onModule.add(block)
    }

    /**
     * Called once for each fragment in each module.
     */
    fun onEachFragment(block: OnFragmentBlock) {
        onFragment.add(block)
    }

    /**
     * Called once for each leaf platform in each module.
     */
    fun onEachLeafPlatform(block: OnPlatformBlock) {
        onPlatform.add(block)
    }

    /**
     * Called once for each custom task across all modules.
     */
    fun onCustomTask(block: OnCustomTaskBlock) {
        onCustomTask.add(block)
    }

    /**
     * Called once for each leaf platform that is a descendant of the given [parentPlatform] in each module.
     *
     * If the module doesn't target any descendant platform of [parentPlatform], [block] is never called.
     */
    fun onEachDescendantPlatformOf(parentPlatform: Platform, block: OnPlatformBlock) {
        onEachLeafPlatform { module, executeOnChangedInputs, plat ->
            if (plat.isDescendantOf(parentPlatform)) {
                block(module, executeOnChangedInputs, plat)
            }
        }
    }

    /**
     * Called once for each task type (main/test), leaf platform, and module combination.
     */
    fun onEachTaskType(block: OnTaskTypeBlock) {
        onTaskType.add { module, executeOnChangedInputs, platform, isTest ->
            block(module, executeOnChangedInputs, platform, isTest)
        }
    }

    /**
     * Called once for each task type (main/test) of each leaf platform that is a descendant of the given
     * [parentPlatform], in each module.
     *
     * If the module doesn't target any descendant platform of [parentPlatform], [block] is never called.
     */
    fun onEachTaskType(parentPlatform: Platform, block: OnTaskTypeBlock) {
        onEachTaskType { module, executeOnChangedInputs, platform, isTest ->
            if (platform.isDescendantOf(parentPlatform)) {
                block(module, executeOnChangedInputs, platform, isTest)
            }
        }
    }

    /**
     * Called once for each leaf platform that is a descendant of the given [parentPlatform] in each module, but only
     * for the given task type (main/test).
     *
     * If the module doesn't target any descendant platform of [parentPlatform], [block] is never called.
     */
    fun onTaskType(parentPlatform: Platform, isTest: Boolean, block: OnTaskTypeBlock) {
        onEachTaskType(parentPlatform) { module, executeOnChangedInputs, platform, test ->
            if (test == isTest) {
                block(module, executeOnChangedInputs, platform, test)
            }
        }
    }

    /**
     * Called once for each build type, task type (main/test), leaf platform, and module combination.
     */
    fun onEachBuildType(block: OnBuildTypeBlock) {
        onBuildType.add { module, execOnChangedInputs, platform, isTest, buildType ->
            block(module, execOnChangedInputs, platform, isTest, buildType)
        }
    }

    /**
     * Called once for each build type, task type (main/test), and leaf platform that is a descendant of the given
     * [parentPlatform] in each module.
     *
     * If the module doesn't target any descendant platform of [parentPlatform], [block] is never called.
     */
    fun onEachBuildType(parentPlatform: Platform, block: OnBuildTypeBlock) {
        onEachBuildType { module, execOnChangedInputs, platform, isTest, buildType ->
            if (platform.isDescendantOf(parentPlatform)) {
                block(module, execOnChangedInputs, platform, isTest, buildType)
            }
        }
    }

    /**
     * Called once for each build type and each leaf platform that is a descendant of the given [parentPlatform] in each
     * module, but only for the given task type (main/test).
     */
    fun onEachBuildType(parentPlatform: Platform, isTest: Boolean, block: OnBuildTypeBlock) {
        onEachBuildType(parentPlatform) { module, execOnChangedInputs, platform, test, buildType ->
            if (test == isTest) {
                block(module, execOnChangedInputs, platform, test, buildType)
            }
        }
    }

    /**
     * Called once for each build type and each leaf platform that is a descendant of the given [parentPlatform] in each
     * module, but only for the 'main' task type.
     *
     * If the module doesn't target any descendant platform of [parentPlatform], [block] is never called.
     */
    fun onMain(parentPlatform: Platform, block: OnBuildTypeBlock) {
        onEachBuildType(parentPlatform, isTest = false, block)
    }

    /**
     * Called once for each build type and each leaf platform that is a descendant of the given [parentPlatform] in each
     * module, but only for the 'test' task type.
     *
     * If the module doesn't target any descendant platform of [parentPlatform], [block] is never called.
     */
    fun onTest(parentPlatform: Platform, block: OnBuildTypeBlock) {
        onEachBuildType(parentPlatform, isTest = true, block)
    }

    /**
     * Called once for each descendant platform of the given [parentPlatform] in each module, but only for the 'main'
     * task type.
     *
     * If the module doesn't target any descendant platform of [parentPlatform], [block] is never called.
     */
    fun onMain(parentPlatform: Platform, block: OnTaskTypeBlock) {
        onTaskType(parentPlatform, isTest = false, block)
    }

    /**
     * Called once for each descendant platform of the given [parentPlatform] in each module, but only for the 'test'
     * task type.
     *
     * If the module doesn't target any descendant platform of [parentPlatform], [block] is never called.
     */
    fun onTest(parentPlatform: Platform, block: OnTaskTypeBlock) {
        onTaskType(parentPlatform, isTest = true, block)
    }

    fun onModuleDependency(
        platform: Platform,
        dependencyReason: DependencyReason,
        block: OnTaskTypeDependencyBlock
    ) {
        onEachTaskType(platform) { module, _, actualPlatform, isTest ->
            module.forModuleDependency(isTest, actualPlatform, dependencyReason) {
                block(module, it, dependencyReason, actualPlatform, isTest)
            }
        }
    }

    fun onCompileModuleDependency(platform: Platform, block: OnTaskTypeDependencyBlock) {
        onModuleDependency(platform, DependencyReason.Compile, block)
    }

    fun onRuntimeModuleDependency(platform: Platform, block: OnTaskTypeDependencyBlock) {
        onModuleDependency(platform, DependencyReason.Runtime, block)
    }

    fun onModuleDependency(
        platform: Platform,
        dependencyReason: DependencyReason,
        block: OnBuildTypeDependencyBlock
    ) {
        onEachBuildType(platform) { module, _, actualPlatform, isTest, buildType ->
            module.forModuleDependency(isTest, actualPlatform, dependencyReason) {
                block(module, it, dependencyReason, actualPlatform, isTest, buildType)
            }
        }
    }

    fun onCompileModuleDependency(platform: Platform, block: OnBuildTypeDependencyBlock) {
        onModuleDependency(platform, DependencyReason.Compile, block)
    }

    fun onRuntimeModuleDependency(platform: Platform, block: OnBuildTypeDependencyBlock) {
        onModuleDependency(platform, DependencyReason.Runtime, block)
    }

    private fun PotatoModule.forModuleDependency(
        isTest: Boolean,
        platform: Platform,
        dependencyReason: DependencyReason,
        block: (dependency: PotatoModule) -> Unit
    ) {
        val fragmentsModuleDependencies = buildDependenciesGraph(isTest, platform, dependencyReason, context.userCacheRoot)
        for (moduleDependency in fragmentsModuleDependencies.getModuleDependencies()) {
            block(moduleDependency)
        }
    }
}

fun PotatoModule.buildDependenciesGraph(
    isTest: Boolean,
    platform: Platform,
    dependencyReason: DependencyReason,
    userCacheRoot: AmperUserCacheRoot
): ModuleDependencyNodeWithModule {
    val resolutionPlatform = platform.toResolutionPlatform()
        ?: throw IllegalArgumentException("Dependency resolution is not supported for the platform $platform")

    return with(moduleDependenciesResolver) {
        resolveDependenciesGraph(
            DependenciesFlowType.ClassPathType(dependencyReason.toResolutionScope(), resolutionPlatform, isTest),
            getCliDefaultFileCacheBuilder(userCacheRoot)
        )
    }
}

private fun ModuleDependencyNodeWithModule.getModuleDependencies(): List<PotatoModule> {
    return distinctBfsSequence { it is ModuleDependencyNodeWithModule }
        .drop(1)
        .filterIsInstance<ModuleDependencyNodeWithModule>()
        .map { it.module }
        .toList()
}

private fun DependencyReason.toResolutionScope() = when(this) {
    DependencyReason.Compile -> ResolutionScope.COMPILE
    DependencyReason.Runtime -> ResolutionScope.RUNTIME
}

/**
 * Returns all fragments in this module that target the given [platform].
 * If [includeTestFragments] is false, only production fragments are returned.
 */
fun PotatoModule.fragmentsTargeting(platform: Platform, includeTestFragments: Boolean): List<Fragment> =
    fragments.filter { (includeTestFragments || !it.isTest) && it.platforms.contains(platform) }
