/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.artifacts.api.ArtifactSelector
import org.jetbrains.amper.tasks.artifacts.api.ArtifactType
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.getModuleDependencies
import kotlin.reflect.KClass

/**
 * Common shortcuts for [ArtifactSelector]s.
 *
 * NOTE: `type` argument is specified explicitly instead of relying on `inline` with `reified` because
 * Kotlin has weird inference semantics concerning return type inference.
 */
object Selectors {
    /**
     * Exactly from the [fragment].
     */
    fun <T : FragmentScopedArtifact, Q : Quantifier> fromFragment(
        type: KClass<T>,
        fragment: Fragment,
        quantifier: Q,
    ): ArtifactSelector<T, Q> {
        val moduleName = fragment.module.userReadableName
        val fragmentName = fragment.name
        return ArtifactSelector(
            type = ArtifactType(type),
            predicate = { it.moduleName == moduleName && it.fragmentName == fragmentName },
            description = "from fragment `$moduleName:$fragmentName`",
            quantifier = quantifier,
        )
    }

    /**
     * From the [fragment] and the fragments it refines.
     */
    fun <T : FragmentScopedArtifact, Q : Quantifier.Multiple> fromFragmentWithDependencies(
        type: KClass<T>,
        fragment: Fragment,
        quantifier: Q,
    ) = fromMatchingFragments<T, Q>(
        type = type,
        module = fragment.module,
        isTest = fragment.isTest,
        hasPlatforms = fragment.platforms,
        quantifier = quantifier,
    ).copy(
        description = "from fragment `${fragment.module.userReadableName}:${fragment.name}` and its dependencies",
    )

    /**
     * From the fragments inside [module] that match [isTest], [hasPlatforms].
     */
    fun <T : FragmentScopedArtifact, Q : Quantifier.Multiple> fromMatchingFragments(
        type: KClass<T>,
        module: AmperModule,
        isTest: Boolean,
        hasPlatforms: Set<Platform>,
        quantifier: Q,
    ): ArtifactSelector<T, Q> {
        val moduleName = module.userReadableName
        return ArtifactSelector(
            type = ArtifactType(type),
            predicate = {
                it.moduleName == moduleName && it.isTest == isTest && it.platforms.containsAll(hasPlatforms)
            },
            description = "from fragments matching ${module.userReadableName}:{isTest=$isTest, platforms=$hasPlatforms} and its dependencies",
            quantifier = quantifier,
        )
    }

    /**
     * From the [leafFragment]'s module and its dependencies, which has [LeafFragment.platform] matching.
     */
    fun <T : PlatformScopedArtifact, Q : Quantifier.Multiple> fromModuleWithDependencies(
        type: KClass<T>,
        leafFragment: LeafFragment,
        userCacheRoot: AmperUserCacheRoot,
        quantifier: Q,
    ): ArtifactSelector<T, Q> {
        val platform = leafFragment.platform
        val moduleNames = buildSet {
            add(leafFragment.module.userReadableName)
            addAll(
                leafFragment.module.getModuleDependencies(
                    isTest = leafFragment.isTest,
                    platform = leafFragment.platform,
                    dependencyReason = ResolutionScope.RUNTIME,
                    userCacheRoot = userCacheRoot,
                ).map { it.userReadableName }
            )
        }
        return ArtifactSelector(
            type = ArtifactType(type),
            predicate = {
                it.moduleName in moduleNames && it.platform == platform
            },
            description = "from module ${leafFragment.module.userReadableName} with platform $platform and its dependencies",
            quantifier = quantifier,
        )
    }
}