/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.api.withTraceFrom
import org.jetbrains.amper.frontend.processing.merge
import org.jetbrains.amper.frontend.schema.Settings

/**
 * Mutates each seed, so they will have propagated settings.
 */
// TODO Can optimize
internal fun Collection<FragmentSeed>.propagateSettingsForSeeds() =
    map { it.withMergedSettingsFromAncestors() }.toSet()

internal fun mergeSettingsFrom(vararg settings: Settings?) = mergeSettingsFrom(settings.toList())

private fun mergeSettingsFrom(settings: Collection<Settings?>) = when {
    settings.isEmpty() -> null
    settings.size == 1 -> settings.first()
    else -> settings.filterNotNull().fold(Settings(), Settings::merge)
}


private fun FragmentSeed.withMergedSettingsFromAncestors(): FragmentSeed {
    // we reverse because we want to apply the furthest settings first (e.g., common) and the specific ones later
    val ancestralPath = ancestralPath().toList().reversed()

    val result = this.copy()
    result.relevantDependencies = relevantDependencies
    result.relevantTestDependencies = relevantTestDependencies

    // the merge operation mutates the receiver, so we need to start from a new Settings() instance,
    // otherwise we'll modify the original fragment settings, and this may affect other resolutions
    result.relevantSettings = mergeSettingsFrom(ancestralPath.map { it.relevantSettings })
        ?.withTraceFrom(ancestralPath.lastOrNull { it.relevantSettings?.trace != null }?.relevantSettings)
    result.relevantTestSettings = mergeSettingsFrom(ancestralPath.map { it.relevantTestSettings })
        ?.withTraceFrom(ancestralPath.lastOrNull { it.relevantTestSettings?.trace != null }?.relevantTestSettings)
    return result
}

//fun FragmentSeed.ancestralPath(): List<FragmentSeed> = buildList {
//    add(this@ancestralPath)
//    dependencies.forEach { addAll(it.ancestralPath()) }
//}.distinct()

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