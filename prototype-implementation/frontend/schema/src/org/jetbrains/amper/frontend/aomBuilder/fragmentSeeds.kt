/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Platform
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
    val modifiersAsStrings: Set<String>,
) {
    val isLeaf by lazy { platforms.singleOrNull()?.isLeaf == true }
    val dependencies = mutableListOf<FragmentSeed>()

    var relevantSettings: Settings? = null
    var relevantTestSettings: Settings? = null

    var relevantDependencies: List<Dependency>? = null
    var relevantTestDependencies: List<Dependency>? = null

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
 * All seed generation is combined into steps:
 * 1. Determining applicable natural hierarchy sub-forest
 * 2. Reducing this hierarchy to the minimal matching one
 * 3. Replacing single leaf fragments by their closest parents (or aliases) if possible.
 * 4. Adding other aliases if needed.
 * 5. Adding common fragment if there is no common natural hierarchy root between generated seeds.
 * 6. Determining dependencies between fragments, based on their platforms.
 *
 * So, with declared platforms:
 * 1. **`[ iosArm64 ]`** - applicable natural hierarchy will be `[ native, apple, ios, iosArm64 ]`,
 * reduced will be `[ ios, iosArm64 ]`, and `iosArm64` is single leaf platform with parent being `ios`,
 * so resulting fragments are **`[ ios ]`**.
 * 2. **`[ iosArm64 ]`** and alias **`myAlias: [ iosArm64 ]`** - applicable natural hierarchy will be
 * `[ native, apple, ios, iosArm64 ]`, reduced will be `[ ios, iosArm64 ]`,
 * and `iosArm64` is single leaf platform with parent being `ios`, but closest matching parent or alias is
 * `myAlias`, so resulting fragments are **`[ myAlias ]`**.
 * 3. **`[ iosArm64, iosSimulatorArm64 ]`** - applicable natural hierarchy will be `[ native, apple, ios, iosArm64, iosSimulatorArm64 ]`,
 * reduced will be `[ ios, iosArm64, iosSimulatorArm64 ]` and there are no single leaf platforms,
 * so result fragments are **`[ ios, iosArm64, iosSimulatorArm64 ]`**
 * 4. **`[ iosArm64, jvm ]`** - applicable natural hierarchy will be `[ jvm, native, apple, ios, iosArm64 ]`,
 * reduced will be `[ jvm, ios, iosArm64 ]`, and there is single leaf `iosArm64`, but no common root,
 * so resulting fragments are **`[ common, jvm, ios ]`**
 * 5. **`[ iosArm64, macosArm64 ]`** - applicable natural hierarchy will be `[ native, apple, macos, macosArm64, ios, iosArm64 ]`,
 * reduced will be `[ apple, macos, macosArm64, ios, iosArm64 ]`, there are two single leaf platforms: `macosArm64` and `iosArm64`,
 * so resulting fragments are **`[ apple, macos, ios ]`**
 */
// TODO Maybe more comprehensive return value to trace things.
fun Module.buildFragmentSeeds(): Collection<FragmentSeed> {
    val declaredPlatforms = product.platforms
    val declaredLeafPlatforms = declaredPlatforms.flatMap { it.leaves }.toSet()

    val declaredAliases = aliases ?: emptyMap()
    val aliases2leafPlatforms = declaredAliases.entries
        .associate { it.key to it.value.flatMap { it.leaves }.toSet() }
    // TODO Check - there should be no aliases with same platforms? Maybe report.
    // Here aliases with same platforms are lost.
    val leafPlatforms2aliases = buildMap {
        aliases2leafPlatforms.entries.sortedBy { it.key }.forEach { putIfAbsent(it , it.key) }
    }

    val applicableHierarchy = Platform.naturalHierarchy.entries
        .filter { (_, v) -> declaredLeafPlatforms.any { it in v } }
        .associate { it.key to it.value }
    val applicableHierarchyAndLeaves = applicableHierarchy +
            Platform.leafPlatforms.filter { it in declaredLeafPlatforms }.associate { it to setOf(it) }

    // Separate platform trees from the overall hierarchy.
    // So, for example, jvm and ios will be in separate trees.
    val hierarchyTrees = applicableHierarchyAndLeaves.entries.groupBy { it.key.topmostParentNoCommon }

    // Reduce hierarchy to minimal matching one. So `[ iosArm64 ]` will remain intact,
    // and `[ native, ios, iosArm64, iosSimulatorArm64 ]` will become `[ ios, iosArm64, iosSimulatorArm64 ]`
    val reduced = buildSet {
        hierarchyTrees.entries.forEach { (_, tree) ->
            val treeLeafPlatforms = tree.map { it.key }.filter { it.isLeaf }.toSet()
            // This is the closest element in the tree, that contains all tree's leaf platforms.
            val closestRoot = tree
                .filter { it.value.containsAll(treeLeafPlatforms) }
                .minByOrNull { it.value.size }
                ?.key
                ?: error("Should not be here: Platform hierarchy tree must contain at least one matching root")
            // Traverse the tree until closest root and add all entries to the reduced hierarchy.
            tree.sortedBy { it.value.size }.takeWhile { it.key != closestRoot }.forEach { add(it.key) }
            add(closestRoot)
        }
    }

    // Threat applicable hierarchy as aliases.
    // Replace single leaves (without common parent) with aliases or parents.
    val combinedAliases = buildMap {
        applicableHierarchy.forEach { put(it.key.pretty, it.value) }
        putAll(aliases2leafPlatforms)
    }

    // Initial seeds, that are computed from the hierarchy and aliases.
    val aliasesLeft = aliases2leafPlatforms.toMutableMap()
    val initialSeeds = buildSet {
        reduced.forEach { platform ->
            // Check if the platform is candidate for replacement by alias.
            if (platform.isLeaf && platform.parentNoCommon !in reduced) {
                val matchingClosestAlias = combinedAliases.entries
                    .filter { platform in it.value }
                    .sortedBy { it.key }
                    // Take alias only if it has only single platform present in module.
                    .filter { alias -> alias.value.filter { it in declaredLeafPlatforms }.size == 1 }
                    .minByOrNull { it.value.size }

                if (matchingClosestAlias != null) {
                    // Mark alias as used. Also mark aliases with same platforms as used.
                    aliasesLeft.remove(matchingClosestAlias.key)
                    combinedAliases.entries
                        .filter { matchingClosestAlias.value == it.value }
                        .forEach { aliasesLeft.remove(it.key) }

                    this += FragmentSeed(
                        platforms = setOf(platform),
                        modifiersAsStrings = setOf(matchingClosestAlias.key),
                    )
                } else {
                    this += FragmentSeed(
                        platforms = setOf(platform),
                        setOf(platform.pretty),
                    )
                }
            } else {
                this += FragmentSeed(
                    platforms = platform.leaves,
                    setOf(platform.pretty),
                )
            }
        }
    }

    // TODO Report warning for aliases with same platforms.
    // Create left aliases seeds.
    val aliasesSeeds = buildSet {
        aliasesLeft.entries
            .distinctBy { it.value }
            .forEach { add(FragmentSeed(it.value, setOf(it.key))) }
    }

    // Get a list of leaf platforms, denoted by modifiers.
    fun Modifiers.convertToLeafPlatforms() =
        // If modifiers are empty, then treat them  like common platform modifiers.
        if (isEmpty()) declaredLeafPlatforms
        // Otherwise, parse every modifier individually.
        else map { it.value }.mapNotNull {
            aliases2leafPlatforms[it] ?: Platform[it]?.leaves // TODO Report if no such platform
        }.flatten().toSet()

    // Create a fragment seed from modifiers like "alias+ios".
    fun Modifiers.convertToSeed(): FragmentSeed? {
        val (areAliases, nonAliases) = partition { aliases2leafPlatforms.contains(it.value) }
        val (arePlatforms, nonPlatforms) = nonAliases.partition { Platform.contains(it.value) }
        val declaredModifierPlatforms = arePlatforms.map { Platform[it.value]!! }.toSet()
        val usedModifierPlatforms = declaredModifierPlatforms.flatMap { it.leaves }.toSet() +
                areAliases.flatMap { aliases2leafPlatforms[it.value] ?: emptyList() }
        // TODO Report nonPlatforms

        val actualLeafPlatforms = usedModifierPlatforms
            .intersect(declaredLeafPlatforms)
            .toSet()
            .takeIf { it.isNotEmpty() } ?: return null
        return FragmentSeed(
            actualLeafPlatforms,
            areAliases.map { it.value }.toSet() + declaredModifierPlatforms.map { it.pretty },
        )
    }

    // TODO Add modifiers from file system. How?
    val allUsedModifiers = (settings.keys +
            `test-settings`.keys +
            dependencies?.keys.orEmpty() +
            `test-dependencies`?.keys.orEmpty()).filter { it.isNotEmpty() }

    val modifiersSeeds = allUsedModifiers.mapNotNull { it.convertToSeed() }

    // ORDER SENSITIVE! Because [FragmentSeed] is compared by its leaf platforms. See [FragmentSeed::equals].
    val requiredSeeds = buildSet {
        addAll(initialSeeds)
        addAll(aliasesSeeds)
        addAll(modifiersSeeds)
    }.toMutableSet()

    // Set up dependencies following platform hierarchy.
    requiredSeeds.forEach { fragmentSeed ->
        val dependencyCandidates = requiredSeeds.filter {
            it.platforms.containsAll(fragmentSeed.platforms) && it != fragmentSeed
        }

        // Exclude all candidates, that include some other candidates entirely.
        fragmentSeed.dependencies += dependencyCandidates.filter { candidate ->
            dependencyCandidates.none { it != candidate && candidate.platforms.containsAll(it.platforms) }
        }
    }

    // And add common fragment if needed.
    if (requiredSeeds.size > 1) {
        val roots = requiredSeeds
            .filter { it.dependencies.isEmpty() }

        if (roots.size > 1) {
            val commonSeed = FragmentSeed(declaredLeafPlatforms, setOf(Platform.COMMON.pretty))
            requiredSeeds.add(commonSeed)
            roots.forEach { it.dependencies.add(commonSeed) }
        }
    }

    // Get leaf-platforms to settings associations.
    val leaves2Settings = settings.entries
        .associate { it.key.convertToLeafPlatforms() to it.value }
    val leaves2TestSettings = `test-settings`.entries
        .associate { it.key.convertToLeafPlatforms() to it.value }

    // Set up relevant settings.
    requiredSeeds.forEach {
        it.relevantSettings = leaves2Settings[it.platforms]
        it.relevantTestSettings = leaves2TestSettings[it.platforms]
    }

    // Get leaf-platforms to dependencies associations.
    val leaves2Dependencies = dependencies?.entries.orEmpty()
        .associate { it.key.convertToLeafPlatforms() to it.value }
    val leaves2TestDependencies = `test-dependencies`?.entries.orEmpty()
        .associate { it.key.convertToLeafPlatforms() to it.value }

    // Set up relevant dependencies.
    requiredSeeds.forEach {
        it.relevantDependencies = leaves2Dependencies[it.platforms]
        it.relevantTestDependencies = leaves2TestDependencies[it.platforms]
    }

    return requiredSeeds
}