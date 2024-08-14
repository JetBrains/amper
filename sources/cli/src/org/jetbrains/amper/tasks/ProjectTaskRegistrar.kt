/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.asLeafFragment
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.dr.resolver.flow.toResolutionPlatform
import org.jetbrains.amper.frontend.dr.resolver.moduleDependenciesResolver
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.resolver.getCliDefaultFileCacheBuilder
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs

typealias OnModelBlock = TaskGraphBuilder.(model: Model, executeOnChangedInputs: ExecuteOnChangedInputs) -> Unit
typealias OnCustomTaskBlock = TaskGraphBuilder.(customTaskDescription: CustomTaskDescription, executeOnChangedInputs: ExecuteOnChangedInputs) -> Unit

typealias OnTaskTypeDependencyBlock = TaskGraphBuilder.(module: PotatoModule, dependsOn: PotatoModule, dependencyReason: DependencyReason, platform: Platform, isTest: Boolean) -> Unit

data class FragmentSelectorCtx(
    val executeOnChangedInputs: ExecuteOnChangedInputs,
    // For decomposing declarations.
    val fragment: Fragment,
    val module: PotatoModule = fragment.module,
    val isTest: Boolean = fragment.isTest,
    val platform: Platform? = fragment.asLeafFragment?.platform,
)

data class ModuleDependencySelectorCtx(
    val executeOnChangedInputs: ExecuteOnChangedInputs,
    val fragment: Fragment,
    val dependsOn: PotatoModule,
    // For decomposing declarations.
    val dependencyReason: DependencyReason,
    val module: PotatoModule = fragment.module,
    val isTest: Boolean = fragment.isTest,
    val platform: Platform? = fragment.asLeafFragment?.platform,
)

typealias FragmentSelectorBlock = TaskGraphBuilder.(FragmentSelectorCtx) -> Unit
typealias ModuleDependenciesSelectorBlock = TaskGraphBuilder.(ModuleDependencySelectorCtx) -> Unit

enum class DependencyReason {
    Compile, Runtime
}

fun interface FragmentSelector {
    companion object : FragmentSelector {
        override fun matches(fragment: Fragment) = true
    }

    fun matches(fragment: Fragment): Boolean
    fun tryGetMatch(eoci: ExecuteOnChangedInputs, fragment: Fragment) =
        if (matches(fragment)) FragmentSelectorCtx(executeOnChangedInputs = eoci, fragment = fragment) else null

    fun and(block: (Fragment) -> Boolean) = FragmentSelector { matches(it) && block(it) }

    /* Select only leaf fragments. */
    fun leafFragments() = and { it is LeafFragment }

    /* Select only fragments with all its platforms being descendants of the passed [platform]. */
    fun platform(platform: Platform) = and { it.platforms.all { it.isDescendantOf(platform) } }

    /* Select fragments which are/are not tests dependint on [isTest] */
    fun test(isTest: Boolean) = and { it.isTest == isTest }

    /* Select only module root fragments. Efectively, that means only one fragment per module. */
    fun rootsOnly() = and { it.fragmentDependencies.isEmpty() }
}

class ProjectTaskRegistrar(val context: ProjectContext, private val model: Model) {
    private val onModel: MutableList<OnModelBlock> = mutableListOf()
    private val onCustomTask: MutableList<OnCustomTaskBlock> = mutableListOf()
    private val selectors: MutableList<Pair<FragmentSelector, FragmentSelectorBlock>> = mutableListOf()

    fun build(): TaskGraph {
        val sortedByPath = model.modules.sortedBy { (it.source as PotatoModuleFileSource).buildFile }
        val tasks = TaskGraphBuilder()
        val eoci = ExecuteOnChangedInputs(context.buildOutputRoot)

        onModel.forEach { it(tasks, model, eoci) }

        for (module in sortedByPath) {
            for (fragment in module.fragments) {
                for ((selector, selectorBlock) in selectors) {
                    selector.tryGetMatch(eoci, fragment)?.let { tasks.selectorBlock(it) }
                }
            }

            module.customTasks.forEach { customTask ->
                onCustomTask.forEach { block ->
                    block(tasks, customTask, eoci)
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

    fun FragmentSelector.select(block: FragmentSelectorBlock) =
        selectors.add(this to block)

    fun FragmentSelector.selectModuleDependencies(
        dependencyReason: DependencyReason,
        block: ModuleDependenciesSelectorBlock,
    ) = select { (executeOnChangedInputs, fragment, module, isTest, platform) ->
        platform ?: return@select
        for (buildType in BuildType.entries) {
            module.forModuleDependency(isTest, platform, dependencyReason) {
                block(ModuleDependencySelectorCtx(executeOnChangedInputs, fragment, it, dependencyReason))
            }
        }
    }

    /**
     * Called once for each custom task across all modules.
     */
    fun onCustomTask(block: OnCustomTaskBlock) {
        onCustomTask.add(block)
    }

    private fun PotatoModule.forModuleDependency(
        isTest: Boolean,
        platform: Platform,
        dependencyReason: DependencyReason,
        block: (dependency: PotatoModule) -> Unit
    ) {
        val fragmentsModuleDependencies =
            buildDependenciesGraph(isTest, platform, dependencyReason, context.userCacheRoot)
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

private fun DependencyReason.toResolutionScope() = when (this) {
    DependencyReason.Compile -> ResolutionScope.COMPILE
    DependencyReason.Runtime -> ResolutionScope.RUNTIME
}

/**
 * Returns all fragments in this module that target the given [platform].
 * If [includeTestFragments] is false, only production fragments are returned.
 */
fun PotatoModule.fragmentsTargeting(platform: Platform, includeTestFragments: Boolean): List<Fragment> =
    fragments.filter { (includeTestFragments || !it.isTest) && it.platforms.contains(platform) }
