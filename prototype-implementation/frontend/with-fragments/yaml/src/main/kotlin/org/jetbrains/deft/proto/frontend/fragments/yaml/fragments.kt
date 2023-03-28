package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.frontend.util.depth
import org.jetbrains.deft.proto.frontend.util.findCommonParent
import org.jetbrains.deft.proto.frontend.util.fragmentName
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName

/**
 * Gets cartesian product of all flavours.
 */
private fun List<Flavor>.cartesian(): List<List<String>> = fold(listOf(listOf())) { acc, flavor ->
    acc + acc.flatMap { current -> flavor.values.map { current + it } }
}

/**
 * Transforms a combination of flavours into fragment suffix.
 */
private fun List<String>.toFlavorSuffix(): String =
    joinToString(separator = "") { flavorValue -> flavorValue.replaceFirstChar { it.uppercase() } }

/**
 * Trims fragment name from flavor and -Test suffixes.
 */
private fun String.trimSuffixes(suffixes: List<String>): String {
    var result = this
    var trimSuffix: String?
    do {
        trimSuffix = suffixes.find { result.endsWith(it) }
        if (trimSuffix != null) {
            result = result.substringBefore(trimSuffix)
        }
    } while (trimSuffix != null)
    return result
}

/**
 * Adds a platform to the [fragmentGroups] with refines of its parents already present in the [fragmentGroups].
 * For proper work requires that all the platform parents are already in the [fragmentGroups].
 */
private fun addPlatformAsFragmentGroup(platform: Platform, fragmentGroups: MutableMap<String, MutableSet<String>>) {
    val platformFragmentName = platform.fragmentName
    val refines = mutableSetOf<String>()
    var parent = platform.parent
    while (parent != null && parent.fragmentName !in fragmentGroups) {
        parent = parent.parent
    }
    if (parent != null) {
        refines.add(parent.fragmentName)
    }
    fragmentGroups[platformFragmentName] = refines
}

/**
 * Topologically sorts graph of strings in a reverse order.
 */
private fun reverseTopologicalSort(startNodes: Set<String>, edges: Map<String, Set<String>>): List<String> {
    val result = mutableListOf<String>()
    val visited = mutableSetOf<String>()
    val greyNodes = mutableSetOf<String>()

    fun dfs(node: String) {
        if (node in visited) return
        if (node in greyNodes) throw ParsingException(
            "Fragment $node is in a cycle: ${
                greyNodes.dropWhile { it != node }.joinToString(" -> ")
            } -> $node"
        )
        greyNodes.add(node)
        val nodeEdges = edges[node] ?: emptySet()
        for (child in nodeEdges) {
            dfs(child)
        }
        greyNodes.remove(node)
        visited.add(node)
        result.add(node)
    }

    for (node in startNodes) {
        dfs(node)
    }
    return result
}

/**
 * Deduces all the fragments and artifacts based on user's input.
 */
internal fun deduceFragments(
    potatoName: String,
    explicitFragments: Map<String, FragmentDefinition>,
    targetPlatforms: Set<Platform>,
    flavors: List<Flavor>,
    type: PotatoModuleType,
): Pair<List<FragmentImpl>, List<ArtifactImpl>> {
    val allPlatforms = targetPlatforms
        .flatMap { p1 -> targetPlatforms.map { p2 -> findCommonParent(p1, p2) } }
        .toMutableSet()
    val fragmentSuffixes = flavors.flatMap { flavor ->
        flavor.values.map { value ->
            value.replaceFirstChar { it.uppercase() }
        }
    } + "Test"
    val fragmentGroupRefines = mutableMapOf<String, MutableSet<String>>()

    for (explicitFragmentName in explicitFragments.keys) {
        val trimmedName = explicitFragmentName.trimSuffixes(fragmentSuffixes)
        val platform = getPlatformFromFragmentName(trimmedName)
        if (platform != null) {
            allPlatforms.add(platform)
        }
    }

    for (platform in allPlatforms.sortedBy { it.depth }) {
        addPlatformAsFragmentGroup(platform, fragmentGroupRefines)
    }

    explicitFragments.forEach { (name, definition) ->
        val trimmedName = name.trimSuffixes(fragmentSuffixes)
        val platform = getPlatformFromFragmentName(trimmedName)
        if (platform == null && trimmedName == name) {
            fragmentGroupRefines[trimmedName] = definition.fragmentDependencies.toMutableSet()
        } else {
            checkNotNull(fragmentGroupRefines[trimmedName]).addAll(definition.fragmentDependencies)
        }
    }

    val topologicallySortedGroups =
        reverseTopologicalSort(targetPlatforms.map { it.fragmentName }.toSet(), fragmentGroupRefines)
    val resultFragments = mutableMapOf<String, FragmentImpl>()

    val flavorCombinations = flavors.cartesian()
    for (groupBaseName in topologicallySortedGroups) {
        for (flavorCombination in flavorCombinations) {
            val flavorSuffix = flavorCombination.toFlavorSuffix()
            val name = "${groupBaseName}${flavorSuffix}"
            val testName = "${groupBaseName}Test${flavorSuffix}"
            val mainFragment = FragmentImpl(
                name = name,
                fragmentDependencies = mutableListOf(),
                externalDependencies = explicitFragments[name]?.externalDependencies ?: emptyList(),
                parts = explicitFragments[name]?.fragmentParts ?: emptySet(),
            )
            val testFragment = FragmentImpl(
                name = testName,
                fragmentDependencies = mutableListOf(
                    FragmentDependencyImpl(
                        mainFragment,
                        FragmentDependencyType.FRIEND
                    )
                ),
                externalDependencies = explicitFragments[testName]?.externalDependencies ?: emptyList(),
                parts = explicitFragments[testName]?.fragmentParts ?: emptySet(),
            )

            fun addRefine(baseName: String, suffix: String) {
                val refineName = "${baseName}${suffix}"
                val refineTestName = "${baseName}Test${suffix}"
                val refineMainFragment = checkNotNull(resultFragments[refineName])
                val refineTestFragment = checkNotNull(resultFragments[refineTestName])
                mainFragment.fragmentDependencies.add(
                    FragmentDependencyImpl(
                        refineMainFragment,
                        FragmentDependencyType.REFINE
                    )
                )
                testFragment.fragmentDependencies.add(
                    FragmentDependencyImpl(
                        refineTestFragment,
                        FragmentDependencyType.REFINE
                    )
                )
            }

            val previousFlavor = if (flavorCombination.isNotEmpty()) flavorCombination.dropLast(1) else null
            if (previousFlavor != null) {
                val previousFlavorSuffix = previousFlavor.toFlavorSuffix()
                addRefine(groupBaseName, previousFlavorSuffix)
            }

            val refines = checkNotNull(fragmentGroupRefines[groupBaseName])
            for (refineBaseName in refines) {
                addRefine(refineBaseName, flavorSuffix)
            }
            resultFragments[name] = mainFragment
            resultFragments[testName] = testFragment
        }
    }

    val allFragments = resultFragments.values.toList()

    val artifacts = when (type) {
        PotatoModuleType.LIBRARY -> listOf(ArtifactImpl(potatoName, allFragments, targetPlatforms))
        PotatoModuleType.APPLICATION -> buildList {
            for (platform in targetPlatforms) {
                for (flavorCombination in flavorCombinations) {
                    if (flavorCombination.size != flavors.size) continue
                    val flavorSuffix = flavorCombination.toFlavorSuffix()
                    val name = "${platform.fragmentName}${flavorSuffix}"
                    val resultFragment = checkNotNull(resultFragments[name])
                    add(ArtifactImpl(potatoName, listOf(resultFragment), setOf(platform)))
                }
            }
        }
    }
    return allFragments to artifacts
}