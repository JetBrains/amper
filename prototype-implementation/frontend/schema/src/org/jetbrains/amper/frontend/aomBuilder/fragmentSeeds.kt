/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.Platform.Companion.leafChildren
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.Settings


/**
 * A seed to create new fragment.
 */
data class FragmentSeed(
    /**
     * Leaf platforms that are the basement for this seed. For example, "iosArm64".
     */
    val platforms: Set<Platform>,

    /**
     * Aliases that form this seed modifier. For example, "androidAndJvm".
     */
    val aliases: Set<String>? = null,

    /**
     * Platforms that form this seed modifier. For example, "ios".
     */
    val rootPlatforms: Set<Platform>? = null,

    var dependency: FragmentSeed? = null,

    var relevantSettings: Settings? = null,
    var relevantTestSettings: Settings? = null,

    var relevantDependencies: List<Dependency>? = null,
    var relevantTestDependencies: List<Dependency>? = null,
) {
    internal val modifiersAsStrings: Set<String> = buildList {
        addAll(aliases.orEmpty())
        addAll(rootPlatforms.orEmpty().map { it.pretty })
    }.toSet()

    // Seeds equality should be based only on its platforms.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FragmentSeed
        return platforms == other.platforms
    }

    override fun hashCode(): Int {
        return platforms.hashCode()
    }
}

/**
 * Return combinations of platforms and aliases
 */
// TODO Maybe more comprehensive return value to trace things.
fun Module.buildFragmentSeeds(): Collection<FragmentSeed> {
    // TODO Check invariant - there should be no aliases with same platforms.
    val productPlatforms = product.value.platforms.value
    val usedAliases = aliases.value ?: emptyMap()
    val aliases2LeafPlatforms = usedAliases.entries
        .associate { it.key to it.value.flatMap { it.leafChildren }.toSet() }
    val aliasPlatforms = usedAliases.values.flatten()

    val combinedLeafPlatforms = (productPlatforms + aliasPlatforms)
        .flatMap { it.leafChildren }
        .toSet()

    // Collect all nodes from natural hierarchy that match collected platforms.
    val applicableNaturalHierarchy = Platform.naturalHierarchy.entries
        .filter { (_, v) -> combinedLeafPlatforms.containsAll(v) }
        .associate { it.key to it.value }

    // Get a list of leaf platforms, denoted by modifiers.
    fun Modifiers.convertToLeafPlatforms() =
        // If modifiers are empty, then treat them  like common platform modifiers.
        if (isEmpty()) combinedLeafPlatforms
        // Otherwise, parse every modifier individually.
        else map { it.value }.mapNotNull {
            aliases2LeafPlatforms[it] ?: Platform[it]?.leafChildren // TODO Report if no such platform
        }.flatten().toSet()

    // Create a fragment seed from modifiers like "alias+ios".
    fun Modifiers.convertToSeed(): FragmentSeed {
        val (areAliases, nonAliases) = partition { aliases2LeafPlatforms.contains(it.value) }
        val (arePlatforms, nonPlatforms) = nonAliases.partition { Platform.contains(it.value) }
        val declaredPlatforms = arePlatforms.map { Platform[it.value]!! }.toSet()
        val usedPlatforms = declaredPlatforms.flatMap { it.leafChildren }.toSet() +
                areAliases.flatMap { aliases2LeafPlatforms[it.value] ?: emptyList() }
        // TODO Report nonPlatforms
        return FragmentSeed(
            usedPlatforms,
            aliases = areAliases.map { it.value }.toSet(),
            rootPlatforms = declaredPlatforms,
        )
    }

    // TODO Add modifiers from file system. How?
    val allUsedModifiers = (settings.modifiers +
            `test-settings`.modifiers +
            dependencies.modifiers +
            `test-dependencies`.modifiers).filter { it.isNotEmpty() }

    // We will certainly create fragments for these.
    val modifiersSeeds = allUsedModifiers.map { it.convertToSeed() }

    val productPlatformSeeds = productPlatforms
        .map { FragmentSeed(it.leafChildren, rootPlatforms = setOf(it)) }

    val aliasesSeeds = aliases2LeafPlatforms.entries
        .map { (key, value) -> FragmentSeed(value, aliases = setOf(key)) }

    val naturalHierarchySeeds = applicableNaturalHierarchy
        .map { (parent, children) -> FragmentSeed(children, rootPlatforms = setOf(parent)) }

    val leafSeeds = combinedLeafPlatforms
        .map { FragmentSeed(setOf(it), rootPlatforms = setOf(it)) }

    // ORDER SENSITIVE!
    val requiredSeeds = buildSet {
        // Aliases have topmost priority.
        addAll(aliasesSeeds)
        // Natural hierarchy is applied first.
        addAll(naturalHierarchySeeds)
        // Then modifiers seeds are applied.
        addAll(modifiersSeeds)

        addAll(productPlatformSeeds)

        // Leaves must be last.
        addAll(leafSeeds)
    }.toMutableSet()

    // Set up dependencies following platform hierarchy.
    requiredSeeds.forEach { fragmentSeed ->
        fragmentSeed.dependency = requiredSeeds
            .filter { it.platforms.containsAll(fragmentSeed.platforms) && it.platforms != fragmentSeed.platforms }
            // TODO Handle case, when alias is the same as natural hierarchy.
            .minByOrNull { it.platforms.size }
    }

    // And add common fragment if needed.
    if (requiredSeeds.size > 1) {
        val roots = requiredSeeds
            .map { generateSequence(it) { it.dependency }.last() }
            .distinct()

        if (roots.size > 1) {
            val commonSeed = FragmentSeed(combinedLeafPlatforms, rootPlatforms = setOf(Platform.COMMON))
            requiredSeeds.add(commonSeed)
            roots.forEach { it.dependency = commonSeed }
        }
    }

    // Get leaf-platforms to settings associations.
    val leaves2Settings = settings.value.entries
        .associate { it.key.convertToLeafPlatforms() to it.value }
    val leaves2TestSettings = `test-settings`.value.entries
        .associate { it.key.convertToLeafPlatforms() to it.value }

    // Set up relevant settings.
    requiredSeeds.forEach {
        it.relevantSettings = leaves2Settings[it.platforms]
        it.relevantTestSettings = leaves2TestSettings[it.platforms]
    }

    // Get leaf-platforms to dependencies associations.
    val leaves2Dependencies = dependencies.value.entries
        .associate { it.key.convertToLeafPlatforms() to it.value }
    val leaves2TestDependencies = `test-dependencies`.value.entries
        .associate { it.key.convertToLeafPlatforms() to it.value }

    // Set up relevant dependencies.
    requiredSeeds.forEach {
        it.relevantDependencies = leaves2Dependencies[it.platforms]
        it.relevantTestDependencies = leaves2TestDependencies[it.platforms]
    }

    return requiredSeeds
}