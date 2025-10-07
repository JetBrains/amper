/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import io.opentelemetry.api.GlobalOpenTelemetry
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModuleAndContext
import org.jetbrains.amper.frontend.dr.resolver.flow.toResolutionPlatform
import org.jetbrains.amper.frontend.dr.resolver.getAmperFileCacheBuilder
import org.jetbrains.amper.frontend.dr.resolver.moduleDependenciesResolver
import org.jetbrains.amper.incrementalcache.IncrementalCache

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
