/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.engine.TaskGraph
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.frontend.PotatoModuleFileSource
import org.jetbrains.amper.util.BuildType
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.jetbrains.amper.util.targetLeafPlatforms
import kotlin.io.path.exists

typealias OnModulePrecondition = (module: PotatoModule) -> Boolean
typealias OnModuleBlock = TaskGraphBuilder.(module: PotatoModule, executeOnChangedInputs: ExecuteOnChangedInputs) -> Unit
typealias OnPlatformPrecondition = (module: PotatoModule, platform: Platform) -> Boolean
typealias OnPlatformBlock = TaskGraphBuilder.(module: PotatoModule, executeOnChangedInputs: ExecuteOnChangedInputs, platform: Platform) -> Unit
typealias OnTaskTypePrecondition = (module: PotatoModule, platform: Platform, isTest: Boolean) -> Boolean
typealias OnTaskTypeBlock = TaskGraphBuilder.(module: PotatoModule, executeOnChangedInputs: ExecuteOnChangedInputs, platform: Platform, isTest: Boolean) -> Unit
typealias OnBuildTypePrecondition = (module: PotatoModule, platform: Platform, isTest: Boolean, buildType: BuildType) -> Boolean
typealias OnBuildTypeBlock = TaskGraphBuilder.(module: PotatoModule, executeOnChangedInputs: ExecuteOnChangedInputs, platform: Platform, isTest: Boolean, buildType: BuildType) -> Unit

typealias OnDependencyPrecondition = (dependencyReason: DependencyReason) -> Boolean
typealias OnTaskTypeDependencyBlock = TaskGraphBuilder.(module: PotatoModule, dependsOn: PotatoModule, dependencyReason: DependencyReason, platform: Platform, isTest: Boolean) -> Unit
typealias OnBuildTypeDependencyBlock = TaskGraphBuilder.(module: PotatoModule, dependsOn: PotatoModule, dependencyReason: DependencyReason, platform: Platform, isTest: Boolean, buildType: BuildType) -> Unit

data class PreconditionedBlock<A, B>(val precondition: A, val block: B) // just pair with renamed fields

enum class DependencyReason {
    Compile, Runtime
}

class ProjectTaskRegistrar(val context: ProjectContext, private val model: Model) {
    private val onModule: MutableList<PreconditionedBlock<OnModulePrecondition, OnModuleBlock>> = mutableListOf()
    private val onPlatform: MutableList<PreconditionedBlock<OnPlatformPrecondition, OnPlatformBlock>> = mutableListOf()
    private val onTaskType: MutableList<PreconditionedBlock<OnTaskTypePrecondition, OnTaskTypeBlock>> = mutableListOf()
    private val onBuildType: MutableList<PreconditionedBlock<OnBuildTypePrecondition, OnBuildTypeBlock>> = mutableListOf()

    fun build(): TaskGraph {
        val sortedByPath = model.modules.sortedBy { (it.source as PotatoModuleFileSource).buildFile }
        val tasks = TaskGraphBuilder()
        val executeOnChangedInputs = ExecuteOnChangedInputs(context.buildOutputRoot)
        for (module in sortedByPath) {
            onModule
                .filter { it.precondition(module) }
                .map { it.block }
                .forEach { tasks.it(module, executeOnChangedInputs) }
            val modulePlatforms = module.targetLeafPlatforms
            for (platform in modulePlatforms) {
                onPlatform
                    .filter { it.precondition(module, platform) }
                    .map { it.block }
                    .forEach { tasks.it(module, executeOnChangedInputs, platform) }
                for (isTest in listOf(false, true)) {
                    onTaskType
                        .filter { it.precondition(module, platform, isTest) }
                        .map { it.block }
                        .forEach { tasks.it(module, executeOnChangedInputs, platform, isTest) }
                    for (buildType in listOf(BuildType.Debug, BuildType.Release)) {
                        onBuildType
                            .filter { it.precondition(module, platform, isTest, buildType) }
                            .map { it.block }
                            .forEach { tasks.it(module, executeOnChangedInputs, platform, isTest, buildType) }
                    }
                }
            }
        }
        return tasks.build()
    }

    fun onModule(precondition: OnModulePrecondition = { true }, block: OnModuleBlock) {
        onModule.add(PreconditionedBlock(precondition, block))
    }

    fun onPlatform(precondition: OnPlatformPrecondition = { _, _ -> true }, block: OnPlatformBlock) {
        onPlatform.add(PreconditionedBlock(precondition, block))
    }

    fun onPlatform(platform: Platform, block: OnPlatformBlock) {
        onPlatform({ _, p -> p.topmostParentNoCommon == platform }, block)
    }

    fun onTaskType(precondition: OnTaskTypePrecondition = { _, _, i -> true }, block: OnTaskTypeBlock) {
        val wrappedPrecondition = { module: PotatoModule, platform: Platform, isTest: Boolean ->
            isNotTestOrThereAreTestSourcesExist(module, isTest, platform) && precondition(module, platform, isTest)
        }
        onTaskType.add(PreconditionedBlock(wrappedPrecondition, block))
    }

    fun onTaskType(platform: Platform, block: OnTaskTypeBlock) {
        onTaskType({ _, p, _ -> p.topmostParentNoCommon == platform }, block)
    }

    fun onTaskType(platform: Platform, isTest: Boolean, block: OnTaskTypeBlock) {
        onTaskType({ _, p, t -> p.topmostParentNoCommon == platform && t == isTest }, block)
    }

    fun onBuildType(precondition: OnBuildTypePrecondition = { _, _, _, _ -> true }, block: OnBuildTypeBlock) {
        val wrappedPrecondition = { module: PotatoModule, platform: Platform, isTest: Boolean, buildType: BuildType ->
            isNotTestOrThereAreTestSourcesExist(module, isTest, platform) && precondition(module, platform, isTest, buildType)
        }
        onBuildType.add(PreconditionedBlock(wrappedPrecondition, block))
    }

    fun onBuildType(platform: Platform, block: OnBuildTypeBlock) {
        onBuildType({ _, p, _, _ -> p.topmostParentNoCommon == platform }, block)
    }

    fun onBuildType(platform: Platform, isTest: Boolean, block: OnBuildTypeBlock) {
        onBuildType({ _, p, t, _ -> p.topmostParentNoCommon == platform && t == isTest }, block)
    }

    fun onMain(platform: Platform, block: OnBuildTypeBlock) {
        onBuildType(platform, false, block)
    }

    fun onTest(platform: Platform, block: OnBuildTypeBlock) {
        onBuildType(platform, true, block)
    }

    fun onMain(platform: Platform, block: OnTaskTypeBlock) {
        onTaskType(platform, false, block)
    }

    fun onTest(platform: Platform, block: OnTaskTypeBlock) {
        onTaskType(platform, true, block)
    }

    fun onModuleDependency(
        platform: Platform,
        precondition: OnDependencyPrecondition = { _ -> true },
        block: OnTaskTypeDependencyBlock
    ) {
        onTaskType(platform) { module, _, actualPlatform, isTest ->
            module.forModuleDependency(isTest, actualPlatform, precondition) {
                block(module, it, DependencyReason.Compile, actualPlatform, isTest)
            }
        }
    }

    fun onCompileModuleDependency(platform: Platform, block: OnTaskTypeDependencyBlock) {
        onModuleDependency(platform, { it == DependencyReason.Compile }, block)
    }

    fun onRuntimeModuleDependency(platform: Platform, block: OnTaskTypeDependencyBlock) {
        onModuleDependency(platform, { it == DependencyReason.Runtime }, block)
    }

    fun onModuleDependency(
        platform: Platform,
        precondition: OnDependencyPrecondition = { _ -> true },
        block: OnBuildTypeDependencyBlock
    ) {
        onBuildType(platform) { module, _, actualPlatform, isTest, buildType ->
            module.forModuleDependency(isTest, actualPlatform, precondition) {
                block(module, it, DependencyReason.Compile, actualPlatform, isTest, buildType)
            }
        }
    }

    fun onCompileModuleDependency(platform: Platform, block: OnBuildTypeDependencyBlock) {
        onModuleDependency(platform, { it == DependencyReason.Compile }, block)
    }

    fun onRuntimeModuleDependency(platform: Platform, block: OnBuildTypeDependencyBlock) {
        onModuleDependency(platform, { it == DependencyReason.Runtime }, block)
    }

    private fun PotatoModule.forModuleDependency(
        isTest: Boolean,
        platform: Platform,
        precondition: OnDependencyPrecondition = { _ -> true },
        block: (dependency: PotatoModule) -> Unit
    ) {
        val fragmentsCompileModuleDependencies = fragmentsModuleDependencies(isTest, platform, DependencyReason.Compile)
        val fragmentsRuntimeModuleDependencies = fragmentsModuleDependencies(isTest, platform, DependencyReason.Runtime)
        if (precondition(DependencyReason.Compile)) {
            forModuleDependency(fragmentsCompileModuleDependencies, platform, DependencyReason.Compile) {
                block(it)
            }
        }
        if (precondition(DependencyReason.Runtime)) {
            forModuleDependency(fragmentsRuntimeModuleDependencies, platform, DependencyReason.Runtime) {
                block(it)
            }
        }
    }

    private fun forModuleDependency(
        fragmentsCompileModuleDependencies: List<PotatoModule>,
        platform: Platform,
        dependencyReason: DependencyReason,
        block: (dependency: PotatoModule) -> Unit
    ) {
        for (compileModuleDependency in fragmentsCompileModuleDependencies) {
            // direct dependencies
            block(compileModuleDependency)

            // exported dependencies
            val exportedModuleDependencies = compileModuleDependency.fragments
                .asSequence()
                .filter { it.platforms.contains(platform) && !it.isTest }
                .flatMap { it.externalDependencies }
                .filterIsInstance<PotatoModuleDependency>()
                .filter { it.dependencyReasonMatches(dependencyReason) && it.exported }
                .map { it.module }
                .toList()

            for (exportedModuleDependency in exportedModuleDependencies) {
                block(exportedModuleDependency)
            }
        }
    }
}

private fun PotatoModuleDependency.dependencyReasonMatches(dependencyReason: DependencyReason) = when (dependencyReason) {
    DependencyReason.Compile -> compile
    DependencyReason.Runtime -> runtime
}

private fun isNotTestOrThereAreTestSourcesExist(
    module: PotatoModule,
    isTest: Boolean,
    platform: Platform
): Boolean {
    val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
    val isNotTestOrThereAreTestSourcesExist = !isTest || !fragments.all { !it.src.exists() }
    return isNotTestOrThereAreTestSourcesExist
}

fun PotatoModule.fragmentsModuleDependencies(
    isTest: Boolean,
    platform: Platform,
    dependencyReason: DependencyReason,
) = fragmentsIncludeProduction(isTest, platform)
    .flatMap { fragment -> fragment.externalDependencies.map { fragment to it } }
    .mapNotNull { (fragment, dependency) ->
        when (dependency) {
            is MavenDependency -> null
            is PotatoModuleDependency -> {
                // runtime dependencies are not required to be in compile tasks graph
                val targetProperty = when (dependencyReason) {
                    DependencyReason.Compile -> dependency.compile
                    DependencyReason.Runtime -> dependency.runtime
                }
                if (targetProperty) {
                    // TODO test with non-resolved dependency on module
                    val resolvedDependencyModule = dependency.module
                    resolvedDependencyModule
                } else {
                    null
                }
            }

            else -> error(
                "Unsupported dependency type: '$dependency' " +
                        "at module '${source}' fragment '${fragment.name}'"
            )
        }
    }

fun PotatoModule.fragmentsIncludeProduction(
    isTest: Boolean,
    platform: Platform
) = fragments.filter { (isTest || !it.isTest) && it.platforms.contains(platform) }
