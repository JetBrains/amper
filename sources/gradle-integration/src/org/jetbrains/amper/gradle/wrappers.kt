/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.jetbrains.amper.frontend.*

/**
 * A class, that caches [modules] and also, adds
 * some other functionality.
 */
data class ModelWrapper(
    val model: Model,
) : Model {
    override val modules = ArrayList(model.modules)
}

class PotatoModuleWrapper(
    private val passedModule: PotatoModule
) : PotatoModule by passedModule {
    // Wrapper functions.
    private val allFragmentWrappers = mutableMapOf<Fragment, FragmentWrapper>()
    val LeafFragment.wrappedLeaf get() = wrapped as LeafFragmentWrapper
    val Fragment.wrapped
        get() = allFragmentWrappers.computeIfAbsent(this) {
            when (this) {
                is FragmentWrapper -> this
                is LeafFragment -> LeafFragmentWrapper(this@PotatoModuleWrapper, this)
                else -> FragmentWrapper(this@PotatoModuleWrapper, this)
            }
        }

    val artifactPlatforms by lazy { artifacts.flatMap { it.platforms }.toSet() }
    val fragmentsByName by lazy { fragments.associateBy { it.name } }
    override val artifacts = passedModule.artifacts.map { it.wrap(this) }
    override val fragments = passedModule.fragments.map { it.wrapped }
    val leafFragments = passedModule.fragments.filterIsInstance<LeafFragment>().map { it.wrappedLeaf }
    val leafNonTestFragments = leafFragments
        .filter { !it.isTest }
    val leafTestFragments = leafFragments
        .filter { it.isTest }

    fun sharedPlatformFragment(platform: Platform, test: Boolean): FragmentWrapper? {
        // find the most common fragment
        val commonFragment = passedModule.fragments.firstOrNull { it.fragmentDependencies.isEmpty() } ?: return null

        // dfs
        val queue = ArrayDeque<Fragment>()
        queue.add(commonFragment)
        while (queue.isNotEmpty()) {
            val fragment = queue.removeFirst()
            if (fragment.platforms == setOf(platform) && fragment.isTest == test) {
                return fragment.wrapped
            }

            fragment.fragmentDependants.forEach {
                queue.add(it.target)
            }
        }

        return null
    }
}

fun Artifact.wrap(module: PotatoModuleWrapper) =
    ArtifactWrapper(this, module)

interface PlatformAware {
    val platforms: Set<Platform>
}

open class ArtifactWrapper(
    artifact: Artifact,
    private val module: PotatoModuleWrapper,
) : Artifact by artifact, PlatformAware {
    override val fragments = artifact.fragments.map { with(module) { it.wrappedLeaf } }
}

open class FragmentWrapper(
    private val module: PotatoModuleWrapper,
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
    module: PotatoModuleWrapper,
    fragment: LeafFragment,
) : FragmentWrapper(module, fragment), LeafFragment by fragment, PlatformAware