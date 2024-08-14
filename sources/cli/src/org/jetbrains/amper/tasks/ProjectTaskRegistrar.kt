/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.frontend.asLeafFragment
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs

typealias OnModelBlock = TaskGraphBuilder.(model: Model, executeOnChangedInputs: ExecuteOnChangedInputs) -> Unit
typealias FragmentSelectorBlock = TaskGraphBuilder.(FragmentSelectorCtx) -> Unit
typealias ModuleDependenciesSelectorBlock = TaskGraphBuilder.(ModuleDependencySelectorCtx) -> Unit

data class FragmentSelectorCtx(
    val executeOnChangedInputs: ExecuteOnChangedInputs,
    // For decomposing declarations.
    val fragment: Fragment,
    val module: PotatoModule = fragment.module,
    val isTest: Boolean = fragment.isTest,
    val platform: Platform? = fragment.asLeafFragment?.platform,
    val buildType: BuildType = BuildType.Debug,
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
    val buildType: BuildType = BuildType.Debug,
)

enum class DependencyReason {
    Compile,
    Runtime,
}

interface FragmentSelector {
    companion object : FragmentSelector {
        override fun matches(fragment: Fragment) = true
    }

    fun matches(fragment: Fragment): Boolean
    fun tryGetMatches(eoci: ExecuteOnChangedInputs, fragment: Fragment) =
        if (matches(fragment)) listOf(FragmentSelectorCtx(executeOnChangedInputs = eoci, fragment = fragment)) else null

    private fun and(block: (Fragment) -> Boolean): FragmentSelector = object : FragmentSelector by this {
        override fun matches(fragment: Fragment) = this@FragmentSelector.matches(fragment) && block(fragment)
    }

    /* Select only leaf fragments. */
    fun leafFragments() = and { it is LeafFragment }

    /* Select only fragments with all its platforms being descendants of the passed [platform]. */
    fun platform(platform: Platform) = and { it.platforms.all { it.isDescendantOf(platform) } }

    /* Select fragments which are/are not tests */
    fun isTest(isTest: Boolean) = and { it.isTest == isTest }

    /* Select only module root fragments. Efectively, that means only one fragment per module. */
    fun rootsOnly() = and { it.fragmentDependencies.isEmpty() }
}

/* Also add iteration through build types */
fun FragmentSelector.iterateBuildTypes(): FragmentSelector = object : FragmentSelector by this {
    override fun tryGetMatches(eoci: ExecuteOnChangedInputs, fragment: Fragment) =
        super.tryGetMatches(eoci, fragment)?.flatMap { ctx -> BuildType.entries.map { ctx.copy(buildType = it) } }
}

/**
 * Convenient way to store block with [FragmentSelector].
 */
data class SelectorAndBlock(
    val selector: FragmentSelector,
    val block: FragmentSelectorBlock,
) {
    fun tryExecute(fragment: Fragment, tasks: TaskGraphBuilder, eoci: ExecuteOnChangedInputs) =
        selector.tryGetMatches(eoci, fragment)?.forEach { tasks.block(it) }
}

class ProjectTaskRegistrar(val context: ProjectContext, private val model: Model) {
    private val onModel: MutableList<OnModelBlock> = mutableListOf()
    private val selectors: MutableList<SelectorAndBlock> = mutableListOf()

    fun build(): TaskGraph {
        val tasks = TaskGraphBuilder()
        val eoci = ExecuteOnChangedInputs(context.buildOutputRoot)

        onModel.forEach { it(tasks, model, eoci) }

        model.modules
            .sortedBy { (it.source as PotatoModuleFileSource).buildFile }
            .flatMap { it.fragments }
            .forEach { fragment ->
                for (it in selectors) it.tryExecute(fragment, tasks, eoci)
            }

        return tasks.build()
    }

    /**
     * Called once for the whole model.
     */
    fun forWholeModel(block: OnModelBlock) =
        onModel.add(block)

    fun FragmentSelector.select(block: FragmentSelectorBlock) =
        selectors.add(SelectorAndBlock(this, block))

    fun FragmentSelector.selectModuleDependencies(
        dependencyReason: DependencyReason,
        block: ModuleDependenciesSelectorBlock,
    ) = select { (executeOnChangedInputs, fragment, module, isTest, platform, buildType) ->
        platform ?: return@select
        module.forModuleDependency(isTest, platform, dependencyReason, context.userCacheRoot) {
            block(
                ModuleDependencySelectorCtx(
                    executeOnChangedInputs = executeOnChangedInputs,
                    fragment = fragment,
                    dependsOn = it,
                    dependencyReason = dependencyReason,
                    buildType = buildType
                )
            )
        }
    }
}