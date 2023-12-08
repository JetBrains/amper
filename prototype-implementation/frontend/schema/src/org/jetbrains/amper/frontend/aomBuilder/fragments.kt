/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.FragmentLink
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.doCapitalize
import org.jetbrains.amper.frontend.mapStartAware
import kotlin.io.path.Path


class DefaultLeafFragment(
    seed: FragmentSeed, isTest: Boolean, externalDependencies: List<Notation>
) : DefaultFragment(seed, isTest, externalDependencies), LeafFragment {
    init {
        assert(seed.rootPlatforms?.firstOrNull()?.isLeaf == true) { "Should be created only for leaf platforms!" }
    }

    override val platform = seed.rootPlatforms!!.single()
}

open class DefaultFragment(
    seed: FragmentSeed,
    override val isTest: Boolean,
    override val externalDependencies: List<Notation>,
) : Fragment {

    /**
     * Modifier that is used to reference this fragment in the module file or within source directory.
     */
    private val modifier =
        if (seed.rootPlatforms == setOf(Platform.COMMON)) ""
        else "@" + seed.modifiersAsStrings.joinToString(separator = "+")

    // TODO Add check for circular dependencies.
    internal val transitiveRefine = buildList<Fragment> {
        fun Fragment.refines() = fragmentDependants.filter { it.type == FragmentDependencyType.REFINE }
        val queue = ArrayDeque<FragmentLink>().apply { addAll(refines()) }
        while (queue.isNotEmpty()) {
            val dep = queue.removeLast().target
            add(dep)
            queue.addAll(dep.refines())
        }
    }

    override val name = seed.modifiersAsStrings
        .mapStartAware { isStart, it -> if (isStart) it else it.doCapitalize() }
        .joinToString()

    override val fragmentDependencies = mutableListOf<FragmentLink>()

    override val fragmentDependants = mutableListOf<FragmentLink>()

    override val platforms = seed.platforms

    override val variants = emptyList<String>()

    override val parts = TODO()

    override val isDefault = true

    override val src = Path("${if (isTest) "test" else "src"}$modifier")

    override val resourcesPath = Path("${if (isTest) "testResources" else "resources"}$modifier")
}

fun createFragments(
    seeds: Collection<FragmentSeed>,
    externalDependencies: Map<Set<String>, List<Notation>>,
    externalTestDependencies: Map<Set<String>, List<Notation>>,
): List<DefaultFragment> {
    data class FragmentBundle(
        val mainFragment: DefaultFragment,
        val testFragment: DefaultFragment,
    )

    fun FragmentSeed.toFragment(isTest: Boolean, externalDependencies: List<Notation>) = when {
        rootPlatforms?.singleOrNull()?.isLeaf == true -> DefaultLeafFragment(this, isTest, externalDependencies)
        else -> DefaultFragment(this, isTest, externalDependencies)
    }

    // Create fragments.
    val initial = seeds.associateWith {
        FragmentBundle(
            it.toFragment(false, externalDependencies[it.modifiersAsStrings].orEmpty()),
            it.toFragment(true, externalTestDependencies[it.modifiersAsStrings].orEmpty()),
        )
    }

    // Set fragment dependencies.
    initial.entries.forEach { (seed, bundle) ->
        // Main fragment dependency.
        bundle.mainFragment.fragmentDependencies +=
            initial[seed.dependency]!!.mainFragment.asRefine()

        initial[seed.dependency]!!.mainFragment.fragmentDependants +=
            bundle.mainFragment.asRefine()

        // Main - test dependency.
        bundle.testFragment.fragmentDependencies +=
            bundle.mainFragment.asFriend()

        bundle.mainFragment.fragmentDependants +=
            bundle.testFragment.asFriend()

        // Test fragment dependency.
        bundle.testFragment.fragmentDependencies +=
            initial[seed.dependency]!!.testFragment.asRefine()

        initial[seed.dependency]!!.testFragment.fragmentDependants +=
            bundle.testFragment.asRefine()
    }

    // Unfold fragments bundles.
    return initial.values.flatMap { listOf(it.mainFragment, it.testFragment) }
}

class SimpleFragmentLink(
    override val target: Fragment,
    override val type: FragmentDependencyType
) : FragmentLink

private fun Fragment.asFriend() = SimpleFragmentLink(this, FragmentDependencyType.FRIEND)
private fun Fragment.asRefine() = SimpleFragmentLink(this, FragmentDependencyType.REFINE)