/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Artifact
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.FragmentDependencyType
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.Platform

class AmperModuleWrapper(
    private val passedModule: AmperModule
) : AmperModule by passedModule {
    // Wrapper functions.
    private val allFragmentWrappers = mutableMapOf<Fragment, FragmentWrapper>()
    val LeafFragment.wrappedLeaf get() = wrapped as LeafFragmentWrapper
    val Fragment.wrapped
        get() = if (this is FragmentWrapper) this else allFragmentWrappers.computeIfAbsent(this) {
            if (this is LeafFragment) LeafFragmentWrapper(this@AmperModuleWrapper, this)
            else FragmentWrapper(this@AmperModuleWrapper, this)
        }

    val artifactPlatforms by lazy { artifacts.flatMap { it.platforms }.toSet() }
    val fragmentsByName by lazy { fragments.associateBy { it.name } }
    override val artifacts = passedModule.artifacts.map { it.wrap(this) }
    override val fragments = passedModule.fragments.map { it.wrapped }
    override val leafFragments = passedModule.fragments.filterIsInstance<LeafFragment>().map { it.wrappedLeaf }
    override val rootFragment = passedModule.rootFragment.wrapped
    override val rootTestFragment = passedModule.rootTestFragment.wrapped
    val leafNonTestFragments = leafFragments.filter { !it.isTest }
    val leafTestFragments = leafFragments.filter { it.isTest }
}

fun Artifact.wrap(module: AmperModuleWrapper) =
    ArtifactWrapper(this, module)

interface PlatformAware {
    val platforms: Set<Platform>
}

open class ArtifactWrapper(
    artifact: Artifact,
    private val module: AmperModuleWrapper,
) : Artifact by artifact, PlatformAware {
    override val fragments = artifact.fragments.map { with(module) { it.wrappedLeaf } }
}

open class FragmentWrapper(
    override val module: AmperModuleWrapper,
    private val fragment: Fragment
) : Fragment by fragment, PlatformAware {
    override fun toString(): String = "FragmentWrapper(fragment=${fragment.name})"

    val refineDependencies by lazy {
        with(module) {
            fragmentDependencies.filter { it.type == FragmentDependencyType.REFINE }.map { it.target.wrapped }
        }
    }
}

@Suppress("DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE")
class LeafFragmentWrapper(
    override val module: AmperModuleWrapper,
    fragment: LeafFragment,
) : FragmentWrapper(module, fragment), LeafFragment by fragment, PlatformAware
