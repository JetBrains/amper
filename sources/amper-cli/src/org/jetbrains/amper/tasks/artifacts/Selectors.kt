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
import org.jetbrains.amper.frontend.allFragmentDependencies
import org.jetbrains.amper.tasks.artifacts.api.ArtifactSelector
import org.jetbrains.amper.tasks.artifacts.api.ArtifactType
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.getModuleDependencies
import kotlin.reflect.KClass

/**
 * Common shortcuts for [ArtifactSelector]s.
 *
 * NOTE: `type` argument is specified explicitly instead of relying on `inline` with `reified` because
 * Kotlin deduces the `T` as an intersection type of explicitly specified property type and the upper `T`'s bound.
 * And for the reified type it "decays" into a common supertype, which becomes out of type bounds.
 * This is a deprecated behavior, but it still allowed in the current Kotlin version.
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
            predicate = { it.fragment == fragment },
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
    ): ArtifactSelector<T, Q> {
        val dependencies by lazy {
            fragment.allFragmentDependencies(includeSelf = true).toSet()
        }
        return ArtifactSelector(
            type = ArtifactType(type),
            predicate = { it.fragment in dependencies },
            description = "from fragment `${fragment.module.userReadableName}:${fragment.name}` and its dependencies",
            quantifier = quantifier,
        )
    }

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
                it.module.userReadableName == moduleName &&
                        it.isTest == isTest &&
                        it.platforms.containsAll(hasPlatforms)
            },
            description = "from fragments matching ${module.userReadableName}:{isTest=$isTest, platforms=$hasPlatforms} and its dependencies",
            quantifier = quantifier,
        )
    }

    /**
     * From the [module] only that matches [platform], [isTest].
     */
    // TODO Should introduce `SingleOrNone` quantifier.
    fun <T : PlatformScopedArtifact> fromModuleOnly(
        type: KClass<T>,
        module: AmperModule,
        isTest: Boolean,
        platform: Platform,
        additionalFilter: (PlatformScopedArtifact) -> Boolean = { true },
    ): ArtifactSelector<T, Quantifier.AnyOrNone> {
        return ArtifactSelector(
            type = ArtifactType(type),
            predicate = {
                it.moduleName == module.userReadableName &&
                        it.platform == platform &&
                        it.isTest == isTest && additionalFilter(it)
            },
            description = "from module ${module.userReadableName} with platform $platform and its dependencies",
            quantifier = Quantifier.AnyOrNone,
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
        includeSelf: Boolean = true,
        additionalFilter: (PlatformScopedArtifact) -> Boolean = { true },
    ): ArtifactSelector<T, Q> {
        val platform = leafFragment.platform
        val modules = buildSet {
            if (includeSelf) add(leafFragment.module)
            addAll(
                leafFragment.module.getModuleDependencies(
                    isTest = leafFragment.isTest,
                    platform = leafFragment.platform,
                    dependencyReason = ResolutionScope.RUNTIME,
                    userCacheRoot = userCacheRoot,
                )
            )
        }
        return ArtifactSelector(
            type = ArtifactType(type),
            predicate = {
                it.module in modules &&
                        it.platform == platform &&
                        it.isTest == leafFragment.isTest &&
                        additionalFilter(it)
            },
            description = "from module ${leafFragment.module.userReadableName} with platform $platform and its dependencies",
            quantifier = quantifier,
        )
    }
}