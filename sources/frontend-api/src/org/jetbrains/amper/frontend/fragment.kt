/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.schema.Settings
import java.nio.file.Path
import java.util.*

/**
 * A part of a module containing its own sources, resources, dependencies, and even toolchain settings.
 *
 * It must respect the *single resolution context* invariant: every source file can be analyzed with a specific set of
 * dependencies that doesn't depend on, say, which platform we're compiling against.
 */
interface Fragment {
    /**
     * The name of this fragment.
     */
    val name: String

    /**
     * The modifier that can be used in the build file or in the source directories names to refer to this fragment.
     *
     * Example: `@mingwX64` is the modifier used in the source directory name `src@mingwX64` for the Windows fragment.
     */
    val modifier: String

    /**
     * The module this fragment belongs to.
     */
    val module: AmperModule

    /**
     * Fragments (within the same module) that this fragment depends on.
     */
    val fragmentDependencies: List<FragmentLink>

    /**
     * Fragments (within the same module) that depend on this fragment.
     */
    val fragmentDependants: List<FragmentLink>

    /**
     * Dependencies of this fragment. They can be Maven modules or other source modules in the project.
     */
    val externalDependencies: List<Notation>

    /**
     * The settings of this fragment, including inherited settings from parent fragments.
     * For instance, the settings of the iosArm64 fragment contain merged settings from iosArm64, ios, native, and common.
     */
    val settings: Settings

    /**
     * Leaf platforms that this fragment is compiled to.
     */
    val platforms: Set<Platform>

    /**
     * Whether this fragment contains test-only sources.
     */
    val isTest: Boolean

    /**
     * Whether this fragment is chosen by default when no variants are specified.
     */
    val isDefault: Boolean

    /**
     * Sources directories.
     */
    val sourceRoots: List<Path>

    /**
     * Resources directories.
     */
    val resourcesPath: Path

    /**
     * Path to compose resources' directory.
     */
    val composeResourcesPath: Path

    /**
     * Whether any processable files are present in [composeResourcesPath].
     */
    val hasAnyComposeResources: Boolean

    /**
     * Paths to the generated source roots that will be used to build the fragment.
     */
    @UsedInIdePlugin
    fun generatedSourceDirs(buildOutputRoot: Path): List<Path>

    /**
     * Paths to the generated resource roots that will be used to build the fragment.
     */
    @UsedInIdePlugin
    fun generatedResourceDirs(buildOutputRoot: Path): List<Path>

    /**
     * Paths to the generated classes roots that will be used to build the fragment.
     */
    @UsedInIdePlugin
    fun generatedClassDirs(buildOutputRoot: Path): List<Path>
}

/**
 * A dependency between fragments.
 */
interface FragmentLink {
    val target: Fragment
    val type: FragmentDependencyType
}

/**
 * A type that describes the relation between a fragment and the fragment it depends on.
 */
enum class FragmentDependencyType {
    /**
     * When fragment A depends on B with a "refines" relationship, it means A can define `actual` declarations for the
     * `expect` declarations of B.
     */
    REFINE,
    /**
     * When fragment A depends on B with a "friends" relationship, it means A can see `internal` declarations from B.
     * Note: this relation is not symmetric.
     */
    FRIEND,
}

/**
 * A [Fragment] that has exactly one target platform.
 *
 * There is a one-to-one mapping between leaf fragments and platform-specific artifacts.
 */
interface LeafFragment : Fragment {
    /**
     * The single platform that this fragment is compiled to.
     */
    val platform: Platform
}

/**
 * Fragments (within the same module) that this fragment depends on with the FRIEND relationship.
 * Internal declarations from these fragments will be visible despite the independent compilation.
 */
val Fragment.friends: List<Fragment>
    get() = fragmentDependencies.filter { it.type == FragmentDependencyType.FRIEND }.map { it.target }

/**
 * Fragments (within the same module) that this fragment depends on with the REFINE relationship.
 * Expect declarations from these fragments will be visible despite a possible independent compilation (for instance,
 * this is useful for metadata compilation).
 */
val Fragment.refinedFragments: List<Fragment>
    get() = fragmentDependencies.filter { it.type == FragmentDependencyType.REFINE }.map { it.target }

/**
 * Source fragments (not Maven) that this fragment depends on with `compile` scope.
 *
 * This includes fragment dependencies from the same module, but also fragments from "external" source module
 * dependencies that support a superset of the platforms.
 *
 * For example, if the `nativeMain` fragment of Module A depends on Module B, [allSourceFragmentCompileDependencies]
 * contains the `commonMain` fragment of Module A, and the `nativeMain` and `commonMain` fragments of Module B.
 */
// TODO this will eventually be more complicated: we need to support other dimensions than target platforms
//  (some sort of attribute matching supporting variants and the likes).
//  It is worth sharing the complete algorithm with dependency resolution (the same thing will be implemented for
//  modules that are downloaded from Maven, using their metadata artifacts).
val Fragment.allSourceFragmentCompileDependencies: List<Fragment>
    get() {
        val fragmentsFromThisModule = fragmentDependencies.map { it.target }
        val fragmentsFromOtherModules = directModuleCompileDependencies.flatMap { module ->
            module.fragmentsTargeting(platforms, includeTestFragments = false)
        }

        // FIXME include transitive exported module dependencies

        return fragmentsFromThisModule + fragmentsFromOtherModules
    }

private val Fragment.directModuleCompileDependencies: List<AmperModule>
    get() = externalDependencies.filterIsInstance<LocalModuleDependency>().filter { it.compile }.map { it.module }

/**
 * Returns the path to the single source root directory of this [Fragment], or fails if there is more than one.
 *
 * The given [reason] should explain why it is expected for this fragment to have only one source root
 * (typically, this function should only be used with fragments that are not allowed to use the maven-like layout).
 */
fun Fragment.singleSourceRoot(reason: String): Path =
    sourceRoots.singleOrNull() ?: error("Expected a single source root in fragment '$name' ($reason). Got:\n${sourceRoots.joinToString("\n")}")

/**
 * Returns all fragments that this fragment depends on transitively, in DFS order.
 */
fun Fragment.allFragmentDependencies(includeSelf: Boolean = false): Sequence<Fragment> = sequence {
    if (includeSelf) {
        yield(this@allFragmentDependencies)
    }
    val traversed = hashSetOf<FragmentLink>()
    val stack = ArrayList(fragmentDependencies)
    while (stack.isNotEmpty<FragmentLink>()) {
        val link = stack.removeLast()
        if (traversed.add(link)) {
            yield(link.target)
            stack.addAll(link.target.fragmentDependencies)
        }
    }
}

/**
 * Returns the path from this [Fragment] to its farthest ancestor (more general parents).
 * Closer parents appear first, even when they're on different paths (BFS order).
 *
 * Example:
 * ```
 *      common
 *      /    \
 *  desktop  apple
 *      \    /
 *     macosX64
 * ```
 * In this situation calling [ancestralPath] on `macosX64` yields `[macosX64, desktop, apple, common]`.
 * The order between `desktop` and `apple` is unspecified.
 */
fun Fragment.ancestralPath(): Sequence<Fragment> = sequence {
    val seenAncestorNames = mutableSetOf<String>()

    val queue = ArrayDeque<Fragment>()
    queue.add(this@ancestralPath)
    while (queue.isNotEmpty()) {
        val fragment = queue.removeFirst()
        yield(fragment)
        fragment.fragmentDependencies.forEach { link ->
            val parent = link.target
            if (parent.name !in seenAncestorNames) {
                queue.add(parent)
                seenAncestorNames.add(parent.name)
            }
        }
    }
}
