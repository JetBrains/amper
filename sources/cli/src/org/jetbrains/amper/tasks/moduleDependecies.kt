/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.dr.resolver.DependenciesFlowType
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.dr.resolver.flow.toResolutionPlatform
import org.jetbrains.amper.frontend.dr.resolver.moduleDependenciesResolver
import org.jetbrains.amper.resolver.getCliDefaultFileCacheBuilder

/**
 * Returns a dependencies sequence of the given module in the resolution scope
 * of the given [platform], [isTest] and [dependencyReason].
 */
internal fun PotatoModule.getModuleDependencies(
    isTest: Boolean,
    platform: Platform,
    dependencyReason: ResolutionScope,
    userCacheRoot: AmperUserCacheRoot,
) : Sequence<PotatoModule> {
    val fragmentsModuleDependencies =
        buildDependenciesGraph(isTest, platform, dependencyReason, userCacheRoot)
    return fragmentsModuleDependencies.getModuleDependencies()
}

internal fun PotatoModule.buildDependenciesGraph(
    isTest: Boolean,
    platform: Platform,
    dependencyReason: ResolutionScope,
    userCacheRoot: AmperUserCacheRoot
): ModuleDependencyNodeWithModule {
    val resolutionPlatform = platform.toResolutionPlatform()
        ?: throw IllegalArgumentException("Dependency resolution is not supported for the platform $platform")

    return with(moduleDependenciesResolver) {
        resolveDependenciesGraph(
            DependenciesFlowType.ClassPathType(dependencyReason, resolutionPlatform, isTest),
            getCliDefaultFileCacheBuilder(userCacheRoot)
        )
    }
}

private fun ModuleDependencyNodeWithModule.getModuleDependencies(): Sequence<PotatoModule> {
    return distinctBfsSequence { it is ModuleDependencyNodeWithModule }
        .drop(1)
        .filterIsInstance<ModuleDependencyNodeWithModule>()
        .map { it.module }
}
