/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.processing.merge
import org.jetbrains.amper.frontend.schema.Settings

/**
 * Returns new fragments with resolved settings, propagated from parents to children.
 */
internal fun List<Fragment>.withPropagatedSettings(): List<Fragment> = map { it.withMergedSettingsFromAncestors() }

private fun Fragment.withMergedSettingsFromAncestors(): Fragment {
    // we reverse because we want to apply the furthest settings first (e.g. common) and the specific ones later
    val ancestralPath = ancestralPath().toList().reversed()
    // the merge operation mutates the receiver, so we need to start from a new Settings() instance,
    // otherwise we'll modify the original fragment settings and this may affect other resolutions
    val mergedSettings = ancestralPath.map { it.settings }.fold(Settings(), Settings::merge)
    return createResolvedAdapter(mergedSettings)
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

