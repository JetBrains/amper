/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.flow

import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.nodeParents
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.PotatoModuleDependency
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.DependencyNodeHolderWithNotation
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule

/**
 * Performs the initial resolution of module classpath dependencies.
 * The resulting graph contains a node for every module dependency
 * as well as direct maven dependencies of that module.
 *
 * It doesn't download anything.
 *
 * Graph:
 * ```
 * ┌─────────────┐
 * │potato-module├────────┐────────────────────┐───────────────────────┐
 * └──┬──────────┘        │                    │                       │
 *    │                   │                    │                       │
 *    │                   │                    │                       │
 * ┌──▼───────────┐     ┌─▼────────────┐     ┌─▼───────────────┐     ┌─▼───────────────┐
 * │potato-module1│...  │potato-moduleN│     │maven dependency1│...  │maven dependencyM│
 * └──┬───────────┘     └─┬────────────┘     └─────────────────┘     └─────────────────┘
 *
 * ```
 */
internal class Classpath(
    dependenciesFlowType: DependenciesFlowType.ClassPathType
): AbstractDependenciesFlow<DependenciesFlowType.ClassPathType>(dependenciesFlowType) {

    override fun directDependenciesGraph(module: PotatoModule, fileCacheBuilder: FileCacheBuilder.() -> Unit): ModuleDependencyNodeWithModule {
        val parentContext = Context {
            this.scope = flowType.scope
            this.platforms = setOf(flowType.platform)
            this.cache = fileCacheBuilder
        }

        return module.fragmentsModuleDependencies(flowType.isTest, parentContext)
    }

    private fun PotatoModule.fragmentsModuleDependencies(
        isTest: Boolean,
        parentContext: Context,
        directDependencies: Boolean = true,
        notation: DefaultScopedNotation? = null,
        visitedModules: MutableSet<PotatoModule> = mutableSetOf()
    ): ModuleDependencyNodeWithModule {

        visitedModules.add(this)

        val moduleContext = parentContext.copyWithNewNodeCache(emptyList(), this.getValidRepositories())

        val platform = moduleContext.settings.platforms.single().toPlatform()
        val scope = moduleContext.settings.scope

        val dependencies = fragmentsTargeting(platform, includeTestFragments = isTest)
            .flatMap { it.toDependencyNode(scope, directDependencies, moduleContext, visitedModules, isTest) }
            .sortedByDescending { it.notation?.exported ?: false }

        val node = ModuleDependencyNodeWithModule(
            module = this,
            name = "${this.userReadableName}:${scope.name}:${platform.name}",
            notation = notation,
            children = dependencies
        )

        return node
    }

    private fun Fragment.toDependencyNode(
        scope: ResolutionScope,
        directDependencies: Boolean,
        moduleContext: Context,
        visitedModules: MutableSet<PotatoModule>,
        isTest: Boolean
    ): List<DependencyNodeHolderWithNotation> {
        val fragmentDependencies = externalDependencies
            .distinct()
            .mapNotNull { dependency ->
                when (dependency) {
                    is MavenDependency -> {
                        val includeDependency = dependency.shouldBeAdded(scope, directDependencies)
                        if (includeDependency) {
                            dependency.toFragmentDirectDependencyNode(this, moduleContext)
                        } else null
                    }

                    is PotatoModuleDependency -> {
                        val resolvedDependencyModule = dependency.module
                        if (!visitedModules.contains(resolvedDependencyModule)) {
                            val includeDependency = dependency.shouldBeAdded(scope, directDependencies)
                            if (includeDependency) {
                                resolvedDependencyModule.fragmentsModuleDependencies(
                                    isTest, moduleContext, directDependencies = false, notation = dependency, visitedModules = visitedModules
                                )
                            } else null
                        } else null
                    }

                    else -> error(
                        "Unsupported dependency type: '$dependency' " +
                                "at module '${module.userReadableName}' fragment '${name}'"
                    )
                }
            }

        return fragmentDependencies
    }

    private fun DefaultScopedNotation.shouldBeAdded(
        scope: ResolutionScope,
        directDependencies: Boolean
    ): Boolean =
        when (scope) {
            // runtime-only dependencies are not required to be in the compile tasks graph
            ResolutionScope.COMPILE -> (compile && (directDependencies || exported))
            ResolutionScope.RUNTIME -> runtime
        }


    /**
     * Returns all fragments in this module that target the given [platform].
     * If [includeTestFragments] is false, only production fragments are returned.
     */
    private fun PotatoModule.fragmentsTargeting(platform: Platform, includeTestFragments: Boolean): List<Fragment> =
        fragments
            .filter { (includeTestFragments || !it.isTest) && it.platforms.contains(platform) }
            .sortedBy { it.name }
            .ensureFirstFragment(platform)

    private fun List<Fragment>.ensureFirstFragment(platform: Platform) =
        if (this.isEmpty() || this[0].platforms.singleOrNull() == platform)
            this
        else {
            val fragmentWithPlatform = this.firstOrNull { it.platforms.singleOrNull() == platform }
            if (fragmentWithPlatform == null) {
                this
            } else
                buildList {
                    add(fragmentWithPlatform)
                    addAll(this@ensureFirstFragment - fragmentWithPlatform)
                }
        }
}