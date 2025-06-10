/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.isParentOfStrict
import org.jetbrains.amper.frontend.leaves
import org.jetbrains.amper.frontend.schema.Module


/**
 * A Utility builder class to prepare settings, fragment dependencies, and platforms
 * to build a set of fragments for a module.
 */
data class FragmentSeed(
    /**
     * Leaf platforms that are the basement for this seed. For example, "iosArm64".
     */
    val platforms: Set<Platform>,

    /**
     * Resulting fragment modifier.
     */
    val modifier: String,

    /**
     * Platform from natural hierarchy, if one was used to create this fragment seed.
     * Will be null only for aliases and complex modifiers like "@ios+android".
     */
    val naturalHierarchyPlatform: Platform?,
) {
    val isLeaf by lazy { naturalHierarchyPlatform?.isLeaf == true }
    val dependencies = mutableSetOf<FragmentSeed>()

    // Seeds equality should be based its [platforms] and possibly [naturalHierarchyPlatform].
    override fun hashCode() = platforms.hashCode() + 31 * naturalHierarchyPlatform.hashCode()
    override fun equals(other: Any?) =
        this === other || (platforms == (other as? FragmentSeed)?.platforms && naturalHierarchyPlatform == other.naturalHierarchyPlatform)
}

/**
 * Creates [FragmentSeed]s for this [Module] based on its declared platforms and aliases.
 *
 * One seed is created for each alias declared in this module, and mapped to the aliased set of leaf platforms.
 *
 * One seed is also created for each platform of the natural hierarchy that contains at least one of the declared
 * leaf platforms of the module. For example, if the module declares `linuxX64`, seeds will be created for
 * `common`, `native`, `linux`, and `linuxX64`.
 *
 * Note: such seeds are only mapped to the subset of their platforms that are actually declared in the module.
 * For example, `native` will only be mapped to `linuxX64` in the above case, not to all other platforms that
 * `native` can include in the natural hierarchy.
 */
fun Module.buildFragmentSeeds(): Set<FragmentSeed> {
    // Get declared platforms.
    val declaredPlatforms = product.platforms
    val declaredLeafPlatforms = declaredPlatforms.flatMap { it.value.leaves }.toSet()

    // Get declared aliases.
    // Check that declared aliases only use declared platforms and
    // check that aliases are not intersecting with any of the hierarchy platforms
    // are implemented within diagnostics.
    val declaredAliases = aliases ?: emptyMap()
    val aliases2leaves = declaredAliases
        .mapValues { it.value.leaves }
        .filterValues { !Platform.naturalHierarchyExt.values.contains(it) }

    // Extract part of the hierarchy that will be used to create fragments.
    val applicableHierarchy = Platform.naturalHierarchyExt.entries
        .filter { (_, v) -> declaredLeafPlatforms.intersect(v).isNotEmpty() }
        // Limit leaf platforms in values by actually declared platforms.
        .associate { it.key to (it.value intersect declaredLeafPlatforms) }

    // Required seeds that are computed from the hierarchy and aliases.
    val resultSeeds = buildSet {

        // Add seeds for applicable hierarchy.
        applicableHierarchy.forEach { (hierarchyPlatform, platforms) ->
            this += FragmentSeed(
                platforms,
                if (hierarchyPlatform == Platform.COMMON) "" else "@${hierarchyPlatform.pretty}",
                hierarchyPlatform,
            )
        }

        // Add seeds for declared aliases.
        aliases2leaves.forEach { (alias, platforms) ->
            this += FragmentSeed(
                platforms,
                "@$alias",
                null,
            )
        }
    }

    return resultSeeds.apply { adjustSeedsDependencies() }
}

/**
 * Utility convenience comparator for [FragmentSeed] that follows dependency semantics.
 * `f1 < f2` => `f1` depends on `f2`.
 */
operator fun FragmentSeed.compareTo(it: FragmentSeed): Int = when {
    it.naturalHierarchyPlatform != null && naturalHierarchyPlatform?.isParentOfStrict(it.naturalHierarchyPlatform) == true -> 1
    naturalHierarchyPlatform != null && it.naturalHierarchyPlatform?.isParentOfStrict(naturalHierarchyPlatform) == true -> -1
    platforms.size > it.platforms.size && platforms.containsAll(it.platforms) -> 1
    it.platforms.size > platforms.size && it.platforms.containsAll(platforms) -> -1
    else -> 0
}

/**
 * Set up dependencies following platform hierarchy.
 */
internal fun Set<FragmentSeed>.adjustSeedsDependencies() = onEach { current ->
    val dependencyCandidates = filter { current < it }
    // Exclude all candidates that depend on other candidates.
    current.dependencies.apply { clear() } += dependencyCandidates.filter { candidate ->
        (dependencyCandidates - candidate).none { it < candidate }
    }
}
