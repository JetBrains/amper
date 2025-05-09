/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.FragmentLink
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.isDescendantOf
import org.jetbrains.amper.tasks.native.NativeTaskType
import org.jetbrains.amper.util.BuildType

internal fun compilationTaskNamesFor(fragment: Fragment): List<TaskName> {
    val leafPlatform = requireNotNull(fragment.platforms.singleOrNull()) {
        "Not a leaf fragment, we don't support compiling intermediate fragments"
    }
    return compilationTaskNamesFor(fragment.module, leafPlatform, fragment.isTest)
}

internal fun compilationTaskNamesFor(
    module: AmperModule,
    platform: Platform,
    isTest: Boolean,
) = when {
    platform.isDescendantOf(Platform.ANDROID) -> BuildType.entries.map { buildType ->
        // TODO: Don't we have fragment per buildType if applicable?
        CommonTaskType.Compile.getTaskName(
            module = module,
            platform = platform,
            isTest = isTest,
            buildType = buildType,
        )
    }

    platform.isDescendantOf(Platform.NATIVE) ->
        listOf(
            NativeTaskType.CompileKLib.getTaskName(
                module = module,
                platform = platform,
                isTest = isTest,
            )
        )

    else -> listOf(
        CommonTaskType.Compile.getTaskName(
            module = module,
            platform = platform,
            isTest = isTest,
        )
    )
}

internal fun refinedLeafFragmentsDependingOn(fragment: Fragment): Collection<LeafFragment> {
    // Fast exit
    if (fragment is LeafFragment) return listOf(fragment)

    val seen = hashSetOf<Fragment>()
    val stack = mutableListOf(fragment)
    return buildList leaves@ {
        while (stack.isNotEmpty()) {  // DFS
            stack.removeLast().takeIf(seen::add)?.let { fragment ->
                if (fragment is LeafFragment)
                    this@leaves.add(fragment)
                fragment.fragmentDependants.asSequence()
                    .filter { it.type == FragmentDependencyType.REFINE }
                    .map(FragmentLink::target)
                    .apply(stack::addAll)
            }
        }
    }
}