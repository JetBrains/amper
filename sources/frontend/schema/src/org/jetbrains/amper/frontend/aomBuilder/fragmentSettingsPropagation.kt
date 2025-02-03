/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.processing.mergeSettings
import org.jetbrains.amper.frontend.schema.Settings

/**
 * Mutates each seed, so they will have propagated settings.
 */
// TODO Can optimize
internal fun Collection<FragmentSeed>.propagateSettingsForSeeds() =
    map { it.withMergedSettingsFromAncestors() }.toSet()

internal fun mergeSettingsFrom(vararg settings: Settings?) = mergeSettingsFrom(settings.toList())

private fun mergeSettingsFrom(settings: Collection<Settings?>): Settings? {
    val nonNullSettings = settings.filterNotNull()
    return if (nonNullSettings.isEmpty()) null
    // We don't need to create the new instance, since [Settings::merge] does this.
    else nonNullSettings.reduce(Settings::mergeSettings)
}

private fun FragmentSeed.withMergedSettingsFromAncestors(): FragmentSeed {
    // we reverse because we want to apply the furthest settings first (e.g., common) and the specific ones later
    val ancestralPath = ancestralPath().toList().reversed()

    val result = this.copy()
    result.seedDependencies = seedDependencies
    result.relevantTestDependencies = relevantTestDependencies
    result.seedSettings = mergeSettingsFrom(ancestralPath.map { it.seedSettings })
    result.seedTestSettings = mergeSettingsFrom(ancestralPath.map { it.seedTestSettings })
    return result
}

fun FragmentSeed.ancestralPath(): Sequence<FragmentSeed> = sequence {
    val seenAncestors = mutableSetOf<FragmentSeed>()

    val queue = ArrayDeque<FragmentSeed>()
    queue.add(this@ancestralPath)
    while(queue.isNotEmpty()) {
        val fragment = queue.removeFirst()
        yield(fragment)
        fragment.dependencies.forEach { parent ->
            if (seenAncestors.add(parent)) queue.add(parent)
        }
    }
}