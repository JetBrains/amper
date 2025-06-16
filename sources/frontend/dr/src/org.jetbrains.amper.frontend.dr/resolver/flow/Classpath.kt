/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver.flow

import org.jetbrains.amper.dependency.resolution.Context
import org.jetbrains.amper.dependency.resolution.FileCacheBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.SpanBuilderSource
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.BomDependency
import org.jetbrains.amper.frontend.DefaultScopedNotation
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LocalModuleDependency
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.allFragmentDependencies
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.DependencyNodeHolderWithNotation
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.fragmentsTargeting

/**
 * Performs the initial resolution of module classpath dependencies.
 * The resulting graph contains a node for every 'compile' module dependency
 * as well as direct maven dependencies of that module.
 *
 * It doesn't download anything.
 *
 * Graph:
 * ```
 * ┌────────────┐
 * │amper-module├────────┐───────────────────┐───────────────────────┐
 * └──┬─────────┘        │                   │                       │
 *    │                  │                   │                       │
 *    │                  │                   │                       │
 * ┌──▼──────────┐     ┌─▼───────────┐     ┌─▼───────────────┐     ┌─▼───────────────┐
 * │amper-module1│...  │amper-moduleN│     │maven dependency1│...  │maven dependencyM│
 * └──┬──────────┘     └─┬───────────┘     └─────────────────┘     └─────────────────┘
 *
 * ```
 *
 * Resolution of the classpath dependencies graph takes the following steps:
 *
 * 1. Resolve complete fragment dependencies graph containing dependency on all other fragments transitively:
 * - adding all fragment dependencies from the same module
 *   (let's call the resulted set as ModuleFragmentDependencies)
 * - adding all direct dependencies on the other modules fragments (for every fragment from ModuleFragmentDependencies)
 * - adding all direct dependencies on the other modules fragments either marked with the flag 'exported'
 *   or unconditionally for native modules since native module compilation classpath includes all transitive dependencies
 *   (for every fragment from the previous step)
 * - repeating the last step until newly added fragments have exported dependencies
 *   => resulted set is a complete transitive fragment dependencies.
 *
 * 2. Now, walk through the resulting fragment dependencies graph and resolve actual maven dependencies
 * - adding maven dependencies of all fragments from ModuleFragmentDependencies unconditionally
 * - adding maven dependencies marked with the flag 'exported' for all the rest fragments from the graph
 */
internal class Classpath(
    dependenciesFlowType: DependenciesFlowType.ClassPathType
): AbstractDependenciesFlow<DependenciesFlowType.ClassPathType>(dependenciesFlowType) {

    override fun directDependenciesGraph(
        module: AmperModule,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        spanBuilder: SpanBuilderSource?
    ): ModuleDependencyNodeWithModule {
        return module.fragmentsModuleDependencies(flowType, fileCacheBuilder = fileCacheBuilder)
    }

    internal fun directDependenciesGraph(fragment: Fragment, fileCacheBuilder: FileCacheBuilder.() -> Unit): ModuleDependencyNodeWithModule {
        return fragment.module.fragmentsModuleDependencies(flowType, initialFragment = fragment, fileCacheBuilder = fileCacheBuilder)
    }

    private fun AmperModule.fragmentsModuleDependencies(
        flowType: DependenciesFlowType.ClassPathType,
        directDependencies: Boolean = true,
        notation: DefaultScopedNotation? = null,
        visitedModules: MutableSet<AmperModule> = mutableSetOf(),
        initialFragment: Fragment? = null,
        fileCacheBuilder: FileCacheBuilder.() -> Unit,
        spanBuilder: SpanBuilderSource? = null,
    ): ModuleDependencyNodeWithModule {

        visitedModules.add(this)

        val moduleContext = resolveModuleContext(flowType.platforms, flowType.scope, fileCacheBuilder, spanBuilder)
        val resolutionPlatforms = moduleContext.settings.platforms

        // test fragments couldn't reference test fragments of transitive (non-direct) module dependencies
        val includeTestFragments = directDependencies && flowType.isTest

        val platforms = resolutionPlatforms.map { it.toPlatform() }.toSet()
        val allMatchingFragments = this.fragmentsTargeting(platforms, includeTestFragments)

        if (initialFragment != null && initialFragment.module.userReadableName != this.userReadableName)
            error ("Given initialFragment doesn't belong to given module")

        val fragments = initialFragment
            ?.allFragmentDependencies(true)
            // it would be better to use simple intersect with allMatchingFragments here, but Fragment.equals is not correctly defined yet
            ?.filter { it.name in allMatchingFragments.map { it.name }}
            ?.toList()
            ?: allMatchingFragments

        val dependencies = fragments
            .sortedForClasspath(platforms)
            .flatMap { it.toDependencyNode(resolutionPlatforms, directDependencies, moduleContext, visitedModules, flowType, fileCacheBuilder) }
            .sortedByDescending { (it.notation as? DefaultScopedNotation)?.exported == true }

        val moduleName = getModuleName(addContextInfo = directDependencies, flowType, resolutionPlatforms)

        val node = ModuleDependencyNodeWithModule(
            module = this,
            name = moduleName.toString(),
            notation = notation,
            children = dependencies,
            templateContext = moduleContext
        )

        return node
    }

    private fun AmperModule.getModuleName(
        addContextInfo: Boolean,
        flowType: DependenciesFlowType.ClassPathType,
        resolutionPlatforms: Set<ResolutionPlatform>
    ): StringBuilder {
        val moduleName = StringBuilder("Module ${this.userReadableName}")
        if (addContextInfo) {
            moduleName.append("\n")
            moduleName.append(
                """│ - ${if (flowType.isTest) "test" else "main"}
                  |│ - scope = ${flowType.scope.name}
                  |│ - platforms = [${resolutionPlatforms.joinToString { it.toPlatform().pretty }}]
                  """.trimMargin()
            )
        }
        return moduleName
    }

    private fun Fragment.toDependencyNode(
        platforms: Set<ResolutionPlatform>,
        directDependencies: Boolean,
        moduleContext: Context,
        visitedModules: MutableSet<AmperModule>,
        flowType: DependenciesFlowType.ClassPathType,
        fileCacheBuilder: FileCacheBuilder.() -> Unit
    ): List<DependencyNodeHolderWithNotation> {
        val fragmentDependencies = externalDependencies
            .distinct()
            .mapNotNull { dependency ->
                when (dependency) {
                    is MavenDependencyBase -> {
                        val includeDependency = dependency.shouldBeAdded(platforms, directDependencies, flowType)
                        if (includeDependency) {
                            dependency.toFragmentDirectDependencyNode(this, moduleContext)
                        } else null
                    }

                    is LocalModuleDependency -> {
                        val resolvedDependencyModule = dependency.module
                        if (!visitedModules.contains(resolvedDependencyModule)) {
                            val includeDependency = dependency.shouldBeAddedByNotion(platforms, directDependencies, flowType)
                            if (includeDependency) {
                                resolvedDependencyModule.fragmentsModuleDependencies(
                                    flowType, directDependencies = false, notation = dependency, visitedModules = visitedModules, fileCacheBuilder = fileCacheBuilder
                                )
                            } else null
                        } else null
                    }

                    is DefaultScopedNotation -> error(
                        "Unsupported dependency type: '$dependency' " +
                                "at module '${module.userReadableName}' fragment '${name}'"
                    )
                }
            }

        return fragmentDependencies
    }

    private fun MavenDependencyBase.shouldBeAdded(
        platforms: Set<ResolutionPlatform>,
        directDependencies: Boolean,
        flowType: DependenciesFlowType.ClassPathType,
    ): Boolean {
        return when(this) {
            is MavenDependency -> {
                shouldBeAddedByNotion(platforms, directDependencies, flowType)
            }
            is BomDependency -> {
                when (flowType.scope) {
                    // BOM affects the compilation classpath of the module where it is declared,
                    // including exported direct dependencies
                    ResolutionScope.COMPILE -> true
                    // BOM affects the runtime classpath of the module and all its consumers
                    ResolutionScope.RUNTIME -> true
                }
            }
        }
    }

    private fun DefaultScopedNotation.shouldBeAddedByNotion(
        platforms: Set<ResolutionPlatform>,
        directDependencies: Boolean,
        flowType: DependenciesFlowType.ClassPathType,
    ): Boolean =
        when (flowType.scope) {
            // the compilation classpath graph contains direct and exported transitive dependencies,
            // for native platforms the compilation classpath graph contains all transitive none-exported dependencies as well,
            // because native compilation (and linking) depends on entire transitive dependencies.
            // runtime-only dependencies are not included in the compilation classpath graph
            ResolutionScope.COMPILE -> compile && (directDependencies || exported || (flowType.includeNonExportedNative && platforms.all { it.nativeTarget != null } ))
            ResolutionScope.RUNTIME -> runtime
        }

    /**
     * Returns all fragments in this module that target the given [platforms].
     */
    private fun Collection<Fragment>.sortedForClasspath(platforms: Set<Platform>): List<Fragment> =
        this
            .sortedBy { it.name }
            .ensureFirstFragment(platforms)

    private fun List<Fragment>.ensureFirstFragment(platforms: Set<Platform>) =
        if (this.isEmpty() || this[0].platforms == platforms)
            this
        else {
            val fragmentWithPlatform = this.firstOrNull { it.platforms == platforms }
            if (fragmentWithPlatform == null) {
                this
            } else
                buildList {
                    add(fragmentWithPlatform)
                    addAll(this@ensureFirstFragment - fragmentWithPlatform)
                }
        }
}
