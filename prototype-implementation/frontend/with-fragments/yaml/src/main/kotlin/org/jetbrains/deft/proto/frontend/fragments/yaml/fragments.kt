package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.FragmentDependencyType
import org.jetbrains.deft.proto.frontend.Platform
import org.jetbrains.deft.proto.frontend.PotatoModuleType
import org.jetbrains.deft.proto.frontend.util.depth
import org.jetbrains.deft.proto.frontend.util.findCommonParent
import org.jetbrains.deft.proto.frontend.util.fragmentName
import org.jetbrains.deft.proto.frontend.util.getPlatformFromFragmentName

/**
 * Gets cartesian product of all variants.
 */
private fun List<Variant>.cartesian(): List<List<String>> = fold(listOf(listOf())) { acc, variant ->
    acc + acc.flatMap { current -> variant.values.map { current + it } }
}

/**
 * Transforms a combination of variants into fragment suffix.
 */
private fun List<String>.toVariantSuffix(): String =
    joinToString(separator = "") { variantValue -> variantValue.replaceFirstChar { it.uppercase() } }

/**
 * Trims fragment name from variants and -Test suffix.
 */
private fun String.trimSuffixes(): String {
    return split("+").first().substringBeforeLast("Test")
}

private fun Map<String, FragmentDefinition>.findFragmentDefinition(baseName: String, variants: List<String>): FragmentDefinition? {
    val matchingDefinitions = filterKeys { fragmentName ->
        val fragmentParts = fragmentName.split("+")
        if (baseName != fragmentParts[0]) return@filterKeys false
        val fragmentVariants = fragmentParts.drop(1)
        fragmentVariants.containsAll(variants) && variants.containsAll(fragmentVariants)
    }
    if (matchingDefinitions.size > 1) {
        throw ParsingException("Found duplicating fragment definitions: ${matchingDefinitions.keys}")
    }
    return matchingDefinitions.values.singleOrNull()
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
    variants: List<Variant>,
    type: PotatoModuleType,
): Pair<List<FragmentImpl>, List<ArtifactImpl>> {
    val allPlatforms = targetPlatforms
        .flatMap { p1 -> targetPlatforms.map { p2 -> findCommonParent(p1, p2) } }
        .toMutableSet()
    val fragmentGroupRefines = mutableMapOf<String, MutableSet<String>>()

    for (explicitFragmentName in explicitFragments.keys) {
        val trimmedName = explicitFragmentName.trimSuffixes()
        val platform = getPlatformFromFragmentName(trimmedName)
        if (platform != null) {
            allPlatforms.add(platform)
        }
    }

    for (platform in allPlatforms.sortedBy { it.depth }) {
        addPlatformAsFragmentGroup(platform, fragmentGroupRefines)
    }

    explicitFragments.forEach { (name, definition) ->
        val trimmedName = name.trimSuffixes()
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

    val variantCombinations = variants.cartesian()
    for (groupBaseName in topologicallySortedGroups) {
        for (variantCombination in variantCombinations) {
            val variantSuffix = variantCombination.toVariantSuffix()
            val testBaseName = "${groupBaseName}Test"
            val name = "${groupBaseName}${variantSuffix}"
            val testName = "${testBaseName}${variantSuffix}"
            val explicitMainFragment = explicitFragments.findFragmentDefinition(groupBaseName, variantCombination)
            val explicitTestFragment = explicitFragments.findFragmentDefinition(testBaseName, variantCombination)
            val mainFragment = FragmentImpl(
                name = name,
                fragmentDependencies = mutableListOf(),
                externalDependencies = explicitMainFragment?.externalDependencies ?: emptyList(),
                parts = explicitMainFragment?.fragmentParts ?: emptySet(),
            )
            val testFragment = FragmentImpl(
                name = testName,
                fragmentDependencies = mutableListOf(
                    FragmentDependencyImpl(
                        mainFragment,
                        FragmentDependencyType.FRIEND
                    )
                ),
                externalDependencies = explicitTestFragment?.externalDependencies ?: emptyList(),
                parts = explicitTestFragment?.fragmentParts ?: emptySet(),
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

            val previousVariant = if (variantCombination.isNotEmpty()) variantCombination.dropLast(1) else null
            if (previousVariant != null) {
                val previousVariantSuffix = previousVariant.toVariantSuffix()
                addRefine(groupBaseName, previousVariantSuffix)
            }

            val refines = checkNotNull(fragmentGroupRefines[groupBaseName])
            for (refineBaseName in refines) {
                addRefine(refineBaseName, variantSuffix)
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
                for (variantCombination in variantCombinations) {
                    if (variantCombination.size != variants.size) continue
                    val variantSuffix = variantCombination.toVariantSuffix()
                    val name = "${platform.fragmentName}${variantSuffix}"
                    val resultFragment = checkNotNull(resultFragments[name])
                    add(ArtifactImpl(potatoName, listOf(resultFragment), setOf(platform)))
                }
            }
        }
    }
    return allFragments to artifacts
}