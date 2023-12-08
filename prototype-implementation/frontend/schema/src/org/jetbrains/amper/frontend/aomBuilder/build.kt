/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.Platform.Companion.leafChildren
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.Module

fun Module.buildAom(): PotatoModule {
    val basement = buildFragmentsBasements()

}

/**
 * A seed to create new fragment.
 */
data class FragmentSeed(
    val platforms: Set<Platform>,
    val aliases: Set<String>? = null,
    val rootPlatforms: Set<Platform>? = null,
    var dependency: FragmentSeed? = null,
)

/**
 * Return combinations of platforms and aliases
 */
// TODO Maybe more comprehensive return value to trace things.
fun Module.buildFragmentsBasements(): List<FragmentSeed> {

    // TODO Check invariant - there should be no aliases with same platforms.

    val productPlatforms = product.value.platforms.value
    val usedAliases = aliases.value ?: emptyMap()
    val aliasPlatforms = usedAliases.values.flatten()

    val combinedLeafPlatforms = (productPlatforms + aliasPlatforms)
        .flatMap { it.leafChildren }
        .toSet()

    // Collect all nodes from natural hierarchy that match collected platforms.
    val applicableNaturalHierarchy = Platform.naturalHierarchy.entries
        .filter { (_, v) -> combinedLeafPlatforms.containsAll(v) }
        .associate { it.key to it.value }

    // Create a fragment seed from modifiers like "alias+ios".
    fun Modifiers.convertToSeed(): FragmentSeed {
        val (areAliases, nonAliases) = partition { usedAliases.contains(it.value) }
        val (arePlatforms, nonPlatforms) = nonAliases.partition { Platform.contains(it.value) }
        val declaredPlatforms = arePlatforms.map { Platform[it.value]!! }.toSet()
        val usedPlatforms = declaredPlatforms.flatMap { it.leafChildren }.toSet() +
                areAliases.flatMap { usedAliases[it.value] ?: emptyList() }
        // TODO Report nonPlatforms
        return FragmentSeed(
            aliases = areAliases.map { it.value }.toSet(),
            rootPlatforms = declaredPlatforms,
            platforms = usedPlatforms,
        )
    }

    // TODO Add modifiers from file system. How?
    val allUsedModifiers = settings.modifiers +
            `test-settings`.modifiers +
            dependencies.modifiers +
            `test-dependencies`.modifiers

    val modifiersSeeds = allUsedModifiers.map { it.convertToSeed() }
    val productPlatformSeeds =
        productPlatforms.map { FragmentSeed(rootPlatforms = setOf(it), platforms = it.leafChildren) }
    val aliasesSeeds = usedAliases.entries.map { (key, value) ->
        FragmentSeed(
            aliases = setOf(key),
            platforms = value.flatMap { it.leafChildren }.toSet()
        )
    }
    val naturalHierarchySeeds = applicableNaturalHierarchy.map { (parent, children) ->
        FragmentSeed(
            rootPlatforms = setOf(parent),
            platforms = children
        )
    }
    val leafSeeds = combinedLeafPlatforms.map { FragmentSeed(rootPlatforms = setOf(it), platforms = setOf(it)) }
    // We will certainly create fragments for these.


    val requiredBasement = requiredRawBasement.map { (key, value) ->
        when (key) {
            is String -> FragmentSeed(value, aliases = setOf(key))
            is Set<*> -> FragmentSeed(value, rootPlatforms = key as Set<Platform>)
            is Platform -> FragmentSeed(value, rootPlatforms = setOf(key))
            else -> error("Must not be here")
        }
    } + FragmentSeed(combinedLeafPlatforms, rootPlatforms = setOf(Platform.COMMON))

    // Set up dependencies following platform hierarchy.
    requiredBasement.forEach { fragmentBase ->
        fragmentBase.dependency = requiredBasement
            .filter { it.platforms.containsAll(fragmentBase.platforms) }
            // TODO Handle case, when alias is the same as natural hierarchy.
            .minByOrNull { it.platforms.size }
    }

    return requiredBasement
}

private val ValueBase<out Map<Modifiers, *>>.modifiers get() = value.keys