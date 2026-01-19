/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import io.opentelemetry.api.GlobalOpenTelemetry
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModuleAndContext
import org.jetbrains.amper.frontend.dr.resolver.flow.toResolutionPlatform
import org.jetbrains.amper.frontend.dr.resolver.getAmperFileCacheBuilder
import org.jetbrains.amper.frontend.dr.resolver.getExternalDependencies
import org.jetbrains.amper.frontend.dr.resolver.moduleDependenciesResolver
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.incrementalcache.IncrementalCache
import kotlin.lazy

/**
 * Returns a dependencies sequence of the given module in the resolution scope
 * of the given [platform], [isTest] and [dependencyReason].
 */
internal fun AmperModule.getModuleDependencies(
    isTest: Boolean,
    platform: Platform,
    dependencyReason: ResolutionScope,
    userCacheRoot: AmperUserCacheRoot,
    incrementalCache: IncrementalCache
) : Sequence<AmperModule> {
    val fragmentsModuleDependencies =
        buildDependenciesGraph(isTest, platform, dependencyReason, userCacheRoot, incrementalCache)
    return fragmentsModuleDependencies.getModuleDependencies()
}

internal fun AmperModule.buildDependenciesGraph(
    isTest: Boolean,
    platform: Platform,
    dependencyReason: ResolutionScope,
    userCacheRoot: AmperUserCacheRoot,
    incrementalCache: IncrementalCache
): ModuleDependencyNodeWithModuleAndContext = buildDependenciesGraph(isTest, setOf(platform), dependencyReason, userCacheRoot, incrementalCache)

internal fun AmperModule.buildDependenciesGraph(
    isTest: Boolean,
    platforms: Set<Platform>,
    dependencyReason: ResolutionScope,
    userCacheRoot: AmperUserCacheRoot,
    incrementalCache: IncrementalCache
): ModuleDependencyNodeWithModuleAndContext {
    val resolutionPlatform = platforms.map { it.toResolutionPlatform()
        ?: throw IllegalArgumentException("Dependency resolution is not supported for the platform $it") }.toSet()

    return with(moduleDependenciesResolver) {
        resolveDependenciesGraph(
            DependenciesFlowType.ClassPathType(dependencyReason, resolutionPlatform, isTest),
            getAmperFileCacheBuilder(userCacheRoot),
            GlobalOpenTelemetry.get(),
            incrementalCache
        )
    }
}

private fun ModuleDependencyNodeWithModuleAndContext.getModuleDependencies(): Sequence<AmperModule> {
    return distinctBfsSequence { child, _ ->  child is ModuleDependencyNodeWithModuleAndContext }
        .drop(1)
        .filterIsInstance<ModuleDependencyNodeWithModuleAndContext>()
        .map { it.module }
}

/**
 * Provides dependencies graphs for all module leaf platforms.
 *
 * Graphs are built based on AOM and are unresolved.
 * (i.e., only effective direct dependencies of modules are included,
 *  external transitive dependencies are not resolved and are absent in the resulting graphs,
 *  constructing unresolved graphs is done without NETWORK access)
 */
class ModuleDependencies(internal val module: AmperModule, userCacheRoot: AmperUserCacheRoot, incrementalCache: IncrementalCache) {

    val mainDeps: Map<Platform, PerPlatformDependencies> =
        module.perPlatformDependencies(false, userCacheRoot, incrementalCache)

    val testDeps: Map<Platform, PerPlatformDependencies> =
        module.perPlatformDependencies(true, userCacheRoot, incrementalCache)

    private fun AmperModule.perPlatformDependencies(
        isTest: Boolean, userCacheRoot: AmperUserCacheRoot, incrementalCache: IncrementalCache,
    ): Map<Platform, PerPlatformDependencies> =
        leafPlatforms
            .sortedBy { it.name }
            .associateBy(keySelector = { it }) {
                PerPlatformDependencies(it, isTest = isTest, this, userCacheRoot, incrementalCache)
            }

    /**
     * Module dependencies resolution should be performed based on the entire list returned by this method at once,
     * so that versions of module dependencies are aligned across all module fragments.
     *
     * @return the list of module root nodes (one root node per platform).
     * Each node from the list contains unresolved platform-specific dependencies of the module.
     */
    fun forResolution(isTest: Boolean): List<ModuleDependencyNodeWithModuleAndContext> {
        // Test dependencies contain dependencies of both test and corresponding main fragments
        val perPlatformDeps = if (isTest) testDeps else mainDeps

        return buildList {
            perPlatformDeps.values.forEach {
                add(it.compileDeps)
                it.runtimeDeps?.let { runtimeDeps -> add(runtimeDeps) }
            }
        }
    }

    /**
     * @return unresolved compile/runtime module dependencies for the particular platform.
     */
    fun forPlatform(platform: Platform, isTest: Boolean): PerPlatformDependencies {
        // Test dependencies contain dependencies of both test and corresponding main fragments
        val perPlatformDeps = if (isTest) testDeps else mainDeps
        return perPlatformDeps[platform]
            ?: error("Dependencies for $platform are not calculated")
    }
}

class PerPlatformDependencies(
    platform: Platform,
    isTest: Boolean,
    module: AmperModule,
    userCacheRoot: AmperUserCacheRoot,
    incrementalCache: IncrementalCache,
) {
    /**
     * This node represents a graph that contains external COMPILE dependencies of the module for the particular platform.
     * It contains direct external dependencies of this module as well as exported dependencies of dependent modules
     * accessible from this module.
     * It doesn't contain transitive external dependencies (no resolution happened actually).
     * This graph is a part of the input for dependency resolution of the module.
     * See [ModuleDependencies.forResolution] for further details.
     */
    val compileDeps: ModuleDependencyNodeWithModuleAndContext by lazy {
        module.buildDependenciesGraph(
            isTest = isTest,
            platform = platform,
            dependencyReason = ResolutionScope.COMPILE,
            userCacheRoot = userCacheRoot,
            incrementalCache = incrementalCache,
        )
    }

    /**
     * This node represents a graph that contains external RUNTIME dependencies of the module for the particular platform.
     * It contains direct external dependencies of this module as well as direct external dependencies of all modules
     * this one depends on transitively.
     * It doesn't contain transitive external dependencies although (no resolution happened actually).
     * This graph is a part of the input for dependency resolution of the module.
     * See [ModuleDependencies.forResolution] for further details.
     */
    val runtimeDeps: ModuleDependencyNodeWithModuleAndContext? by lazy {
        when {
            platform.isDescendantOf(Platform.NATIVE) -> null  // The native world doesn't distinguish compile/runtime classpath
            else -> module.buildDependenciesGraph(
                isTest = isTest,
                platform = platform,
                dependencyReason = ResolutionScope.RUNTIME,
                userCacheRoot = userCacheRoot,
                incrementalCache = incrementalCache,
            )
        }
    }

    val compileDepsCoordinates: List<MavenCoordinates> by lazy { compileDeps.getExternalDependencies() }
    val runtimeDepsCoordinates: List<MavenCoordinates> by lazy { runtimeDeps?.getExternalDependencies() ?: emptyList() }
}
