/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.FragmentLink
import org.jetbrains.amper.frontend.Notation
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.pretty
import org.jetbrains.amper.frontend.schema.Modifiers
import kotlin.io.path.Path


class SimpleFragment(
    seed: FragmentSeed,
    override val isTest: Boolean,
    override val externalDependencies: List<Notation>,
) : Fragment {
    override val name = seed.aliases ?: seed.rootPlatforms!!.pretty

    override val fragmentDependencies = mutableListOf<FragmentLink>()

    override val fragmentDependants = mutableListOf<FragmentLink>()

    override val platforms = seed.platforms

    override val variants = emptyList<String>()

    override val parts = TODO()

    override val isDefault = true

    override val src = Path(buildString {
        if (isTest) append("test") else append("src")
        appendSuffix(seed)
    })

    override val resourcesPath = Path(buildString {
        if (isTest) append("testResources") else append("resources")
        appendSuffix(seed)
    })

    private fun StringBuilder.appendSuffix(seed: FragmentSeed) {
        if (seed.aliases != null) append("@${seed.aliases}")
        if (seed.rootPlatforms != null && seed.rootPlatforms != Platform.COMMON) append("@${seed.rootPlatforms.pretty}")
    }
}

fun createFragments(
    seeds: Collection<FragmentSeed>,
    externalDependencies: Map<Modifiers, List<Notation>>,
    externalTestDependencies: Map<Modifiers, List<Notation>>,
): List<Fragment> {
    data class FragmentBundle(
        val mainFragment: SimpleFragment,
        val testFragment: SimpleFragment,
    )

    fun FragmentSeed.getExternalDependencies() =
        externalDependencies[]

    // Create fragments.
    val initial = seeds.associateWith {
        FragmentBundle(
            SimpleFragment(it, false, TODO()),
            SimpleFragment(it, true, TODO()),
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