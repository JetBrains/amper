/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.processing.merge
import org.jetbrains.amper.frontend.schema.Settings

// TODO move to fragments creation (build.kt / fragments.kt)

internal class ModelImpl(override val modules: List<PotatoModule>) : Model

val Model.resolved: Model
    get() = ModelImpl(
        this@resolved.modules.map { it.withResolvedFragments() }
    )

fun PotatoModule.withResolvedFragments(): PotatoModule = object : PotatoModule by this {
    override val fragments: List<Fragment> = this@withResolvedFragments.fragments.withPropagatedSettings()
}

/**
 * Returns new fragments with resolved settings, propagated from parents to children.
 */
internal fun List<Fragment>.withPropagatedSettings(): List<Fragment> = map { it.withMergedSettingsFromAncestors() }

private fun Fragment.withMergedSettingsFromAncestors(): Fragment {
    val ancestralPath = ancestralPath().reversed()
    // the merge operation mutates the receiver, so we need to start from a new Settings() instance,
    // otherwise we'll modify the original fragment settings and this may affect other resolutions
    val mergedSettings = ancestralPath.map { it.settings }.fold(Settings(), Settings::merge)
    return createResolvedAdapter(mergedSettings)
}

/**
 * Returns the path from this [Fragment] to its farthest ancestor (more general parents).
 *
 * Closer parents appear first, even when they're on different paths:
 * ```
 *      common
 *      /    \
 *  desktop  apple
 *      \    /
 *     macosX64
 * ```
 *
 * Yields: `[macosX64, desktop, apple, common]`
 */
private fun Fragment.ancestralPath(): List<Fragment> {
    val seenAncestorNames = mutableSetOf<String>()
    val sortedAncestors = mutableListOf<Fragment>()

    val queue = ArrayDeque<Fragment>()
    queue.add(this)
    while(queue.isNotEmpty()) {
        val fragment = queue.removeFirst()
        sortedAncestors.add(fragment)
        fragment.fragmentDependencies.forEach { link ->
            val parent = link.target
            if (parent.name !in seenAncestorNames) {
                queue.add(parent)
                seenAncestorNames.add(parent.name)
            }
        }
    }
    return sortedAncestors
}

private fun Fragment.createResolvedAdapter(mergedSettings: Settings) = when (this@createResolvedAdapter) {
    is LeafFragment -> object : LeafFragment by this@createResolvedAdapter {
        override val settings = mergedSettings
        override fun equals(other: Any?) = other != null && other is LeafFragment && name == other.name
        override fun hashCode() = name.hashCode()
    }
    else -> object : Fragment by this@createResolvedAdapter {
        override val settings = mergedSettings
        override fun equals(other: Any?) = other != null && other is LeafFragment && name == other.name
        override fun hashCode() = name.hashCode()
    }
}

